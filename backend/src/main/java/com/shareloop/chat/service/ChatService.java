package com.shareloop.chat.service;

import com.shareloop.chat.ChatErrorCode;
import com.shareloop.chat.dto.MessageResponse;
import com.shareloop.chat.dto.SendMessageRequest;
import com.shareloop.chat.entity.Message;
import com.shareloop.chat.mapper.MessageMapper;
import com.shareloop.chat.repository.MessageRepository;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.RequestChatContext;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.service.RequestService;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý logic gửi tin nhắn và lấy tin nhắn theo chu kỳ polling (FR-68, BR-C03).
 */
@Service
public class ChatService {

    private static final Set<RequestStatus> ALLOWED_CHAT_STATUSES = Set.of(
            RequestStatus.RESERVED,
            RequestStatus.AWAITING_LOGISTICS,
            RequestStatus.LOGISTICS_CONFIRMED,
            RequestStatus.AWAITING_HANDOVER,
            RequestStatus.COMPLETED,
            RequestStatus.DISPUTED);

    private final MessageRepository messageRepository;
    private final RequestService requestService;
    private final ChatContactFilter chatContactFilter;

    public ChatService(
            MessageRepository messageRepository, RequestService requestService, ChatContactFilter chatContactFilter) {
        this.messageRepository = messageRepository;
        this.requestService = requestService;
        this.chatContactFilter = chatContactFilter;
    }

    /**
     * Gửi tin nhắn mới trong khung chat của yêu cầu giao dịch (FR-68, FR-72).
     *
     * @param requestId     id của yêu cầu giao dịch
     * @param currentUserId id người gửi hiện tại
     * @param request       nội dung tin nhắn
     * @return MessageResponse thông tin tin nhắn đã lưu
     */
    @Transactional
    public MessageResponse sendMessage(long requestId, Long currentUserId, SendMessageRequest request) {
        if (currentUserId == null) {
            throw new BusinessException(ChatErrorCode.CHAT_FORBIDDEN);
        }

        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new BusinessException(ChatErrorCode.CHAT_MESSAGE_EMPTY);
        }

        RequestChatContext context = validateAndGetChatContext(requestId, currentUserId);

        // Lọc thông tin liên hệ tầng 1 (FR-72, FR-74; BR-C01)
        chatContactFilter.validateMessage(request.content());

        Message message = new Message(
                context.requestId(), currentUserId, request.content().trim());
        Message saved = messageRepository.save(message);

        return MessageMapper.toResponse(saved);
    }

    /**
     * Lấy danh sách tin nhắn theo chu kỳ (polling), hỗ trợ lọc tin mới hơn afterId (FR-68).
     *
     * @param requestId     id của yêu cầu giao dịch
     * @param currentUserId id người đang thao tác
     * @param afterId       id tin nhắn mốc để lấy các tin nhắn mới hơn (nếu có)
     * @return danh sách tin nhắn sắp xếp theo thời gian tăng dần
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(long requestId, Long currentUserId, Long afterId) {
        if (currentUserId == null) {
            throw new BusinessException(ChatErrorCode.CHAT_FORBIDDEN);
        }

        validateAndGetChatContext(requestId, currentUserId);

        List<Message> messages;
        if (afterId != null && afterId > 0) {
            messages = messageRepository.findByRequestIdAndIdGreaterThanOrderByIdAsc(requestId, afterId);
        } else {
            messages = messageRepository.findByRequestIdOrderByIdAsc(requestId);
        }

        return messages.stream().map(MessageMapper::toResponse).toList();
    }

    /**
     * Xác thực quyền truy cập và trạng thái hợp lệ của cuộc trò chuyện.
     */
    private RequestChatContext validateAndGetChatContext(long requestId, long currentUserId) {
        RequestChatContext context;
        try {
            context = requestService.getChatContext(requestId);
        } catch (BusinessException e) {
            if (e.getErrorCode() == RequestErrorCode.REQUEST_NOT_FOUND
                    || e.getErrorCode() == RequestErrorCode.REQUEST_ITEM_NOT_FOUND) {
                throw new BusinessException(ChatErrorCode.CHAT_REQUEST_NOT_FOUND);
            }
            throw e;
        }

        // 1. Kiểm tra trạng thái: Khung chat chỉ mở khi Request từ RESERVED trở lên
        if (!ALLOWED_CHAT_STATUSES.contains(context.status())) {
            throw new BusinessException(ChatErrorCode.CHAT_REQUEST_NOT_RESERVED);
        }

        // 2. Kiểm tra quyền: Chỉ hai bên của yêu cầu (chủ bài đăng và người nhận) mới được tham gia
        boolean isParty = currentUserId == context.receiverId() || currentUserId == context.donorId();
        if (!isParty) {
            throw new BusinessException(ChatErrorCode.CHAT_FORBIDDEN);
        }

        return context;
    }
}
