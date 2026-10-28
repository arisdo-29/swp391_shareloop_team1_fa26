package com.shareloop.chat.service;

import com.shareloop.chat.ChatErrorCode;
import com.shareloop.chat.dto.MessageResponse;
import com.shareloop.chat.dto.SendMessageRequest;
import com.shareloop.chat.entity.Message;
import com.shareloop.chat.entity.MessageKind;
import com.shareloop.chat.entity.MessageModeration;
import com.shareloop.chat.mapper.MessageMapper;
import com.shareloop.chat.repository.MessageRepository;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.ItemCardDto;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.media.service.MediaService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.RequestChatContext;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.service.RequestService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý logic gửi tin nhắn, chia sẻ thẻ bài và lấy thẻ ghim ở đầu chat (FR-68, FR-69, FR-70, FR-72, FR-75, FR-76, FR-78).
 */
@Service
public class ChatService {

    private static final Set<RequestStatus> ALLOWED_READ_CHAT_STATUSES = Set.of(
            RequestStatus.RESERVED,
            RequestStatus.AWAITING_LOGISTICS,
            RequestStatus.LOGISTICS_CONFIRMED,
            RequestStatus.AWAITING_HANDOVER,
            RequestStatus.COMPLETED,
            RequestStatus.DISPUTED,
            RequestStatus.CANCELLED);

    private static final Set<RequestStatus> ALLOWED_SEND_CHAT_STATUSES = Set.of(
            RequestStatus.RESERVED,
            RequestStatus.AWAITING_LOGISTICS,
            RequestStatus.LOGISTICS_CONFIRMED,
            RequestStatus.AWAITING_HANDOVER,
            RequestStatus.COMPLETED,
            RequestStatus.DISPUTED);

    private final MessageRepository messageRepository;
    private final RequestService requestService;
    private final ChatContactFilter chatContactFilter;
    private final MediaService mediaService;
    private final ItemQueryService itemQueryService;
    private final ChatViolationRecorder chatViolationRecorder;
    private final ChatAiModerationService chatAiModerationService;

    public ChatService(
            MessageRepository messageRepository,
            RequestService requestService,
            ChatContactFilter chatContactFilter,
            MediaService mediaService) {
        this(messageRepository, requestService, chatContactFilter, mediaService, null, null, null);
    }

    public ChatService(
            MessageRepository messageRepository,
            RequestService requestService,
            ChatContactFilter chatContactFilter,
            MediaService mediaService,
            ItemQueryService itemQueryService) {
        this(messageRepository, requestService, chatContactFilter, mediaService, itemQueryService, null, null);
    }

    @Autowired
    public ChatService(
            MessageRepository messageRepository,
            RequestService requestService,
            ChatContactFilter chatContactFilter,
            MediaService mediaService,
            @Autowired(required = false) ItemQueryService itemQueryService,
            @Autowired(required = false) ChatViolationRecorder chatViolationRecorder,
            @Autowired(required = false) ChatAiModerationService chatAiModerationService) {
        this.messageRepository = messageRepository;
        this.requestService = requestService;
        this.chatContactFilter = chatContactFilter;
        this.mediaService = mediaService;
        this.itemQueryService = itemQueryService;
        this.chatViolationRecorder = chatViolationRecorder;
        this.chatAiModerationService = chatAiModerationService;
    }

    /**
     * Gửi tin nhắn mới (văn bản, ảnh hoặc thẻ bài đăng) trong khung chat (FR-68, FR-70, FR-72, FR-75, FR-76, FR-78).
     *
     * @param requestId     id của yêu cầu giao dịch
     * @param currentUserId id người gửi hiện tại
     * @param request       nội dung tin nhắn, ảnh và/hoặc id bài đăng
     * @return MessageResponse thông tin tin nhắn đã lưu
     */
    @Transactional
    public MessageResponse sendMessage(long requestId, Long currentUserId, SendMessageRequest request) {
        if (currentUserId == null) {
            throw new BusinessException(ChatErrorCode.CHAT_FORBIDDEN);
        }

        boolean hasContent = request != null
                && request.content() != null
                && !request.content().isBlank();
        boolean hasMedia = request != null && request.mediaId() != null;
        boolean hasRefItem = request != null && request.refItemId() != null;

        if (!hasContent && !hasMedia && !hasRefItem) {
            throw new BusinessException(ChatErrorCode.CHAT_MESSAGE_EMPTY);
        }

        RequestChatContext context = validateAndGetChatContext(requestId, currentUserId);

        // Khung chat chỉ được gửi tin nhắn mới khi giao dịch chưa bị huỷ (từ RESERVED đến DISPUTED)
        if (!ALLOWED_SEND_CHAT_STATUSES.contains(context.status())) {
            throw new BusinessException(ChatErrorCode.CHAT_REQUEST_NOT_RESERVED);
        }

        // Người nhận đồ Cho tặng không gửi ảnh; chủ bài vẫn gửi được, thẻ bài và chữ không bị ảnh hưởng
        if (hasMedia && "GIVE".equals(context.type()) && currentUserId.equals(context.receiverId())) {
            throw new BusinessException(ChatErrorCode.CHAT_IMAGE_GIVE_RECEIVER_FORBIDDEN);
        }

        // Chặn gửi ảnh trước khi hai bên chốt lịch (FR-75, BR-C05)
        if (hasMedia) {
            if (context.status() == RequestStatus.RESERVED || context.status() == RequestStatus.AWAITING_LOGISTICS) {
                throw new BusinessException(ChatErrorCode.CHAT_IMAGE_BEFORE_LOGISTICS_FORBIDDEN);
            }
            // Gắn ảnh qua MediaService để đảm bảo quyền sở hữu và chuyển sang ATTACHED
            mediaService.attach(List.of(request.mediaId()), currentUserId);
        }

        // Kiểm tra bài đăng khi chia sẻ thẻ bài (FR-70, BR-C07)
        ItemCardDto sharedCard = null;
        if (hasRefItem) {
            if (itemQueryService != null) {
                try {
                    sharedCard = itemQueryService.getItemCard(request.refItemId());
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ItemErrorCode.ITEM_NOT_FOUND) {
                        throw new BusinessException(ChatErrorCode.CHAT_ITEM_NOT_FOUND);
                    }
                    throw e;
                }
                if (sharedCard.status() != ItemStatus.APPROVED) {
                    throw new BusinessException(ChatErrorCode.CHAT_ITEM_NOT_APPROVED);
                }
            }
        }

        // Lọc thông tin liên hệ và từ khoá cấm mức BLOCK (FR-72, FR-74, FR-78; BR-C01, BR-A10, BR-C06)
        ChatAiModerationService.Decision aiDecision = null;
        if (hasContent) {
            boolean isBeforeLogistics =
                    context.status() == RequestStatus.RESERVED || context.status() == RequestStatus.AWAITING_LOGISTICS;
            Optional<String> violation = chatContactFilter.checkViolation(request.content(), isBeforeLogistics);
            if (violation.isPresent()) {
                String reason = violation.get();
                if (chatViolationRecorder != null) {
                    chatViolationRecorder.recordViolationAndAlertAdmin(
                            context.requestId(),
                            currentUserId,
                            request.content().trim(),
                            reason);
                }
                throw new BusinessException(
                        ChatErrorCode.CHAT_MESSAGE_BLOCKED, "Tin nhắn bị chặn do chứa " + reason + ".");
            }
            if (isBeforeLogistics) {
                try {
                    chatContactFilter.validateMessage(request.content());
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ChatErrorCode.CHAT_MESSAGE_BLOCKED && chatViolationRecorder != null) {
                        chatViolationRecorder.recordViolationAndAlertAdmin(
                                context.requestId(),
                                currentUserId,
                                request.content().trim(),
                                e.getMessage());
                    }
                    throw e;
                }
                if (chatAiModerationService != null) {
                    aiDecision = chatAiModerationService
                            .evaluate(context.requestId(), request.content())
                            .orElse(null);
                    if (aiDecision != null && aiDecision.blocked()) {
                        if (chatViolationRecorder != null) {
                            chatViolationRecorder.recordViolationAndAlertAdmin(
                                    context.requestId(),
                                    currentUserId,
                                    request.content().trim(),
                                    aiDecision.reason(),
                                    2,
                                    aiDecision.confidence());
                        }
                        throw new BusinessException(
                                ChatErrorCode.CHAT_MESSAGE_BLOCKED,
                                "Tin nhắn bị chặn do chứa " + aiDecision.reason() + ".");
                    }
                }
            }
        }

        Message message;
        String content = hasContent ? request.content().trim() : null;
        if (hasRefItem) {
            message = new Message(
                    context.requestId(),
                    currentUserId,
                    MessageKind.ITEM_CARD,
                    content,
                    request.mediaId(),
                    request.refItemId());
        } else if (hasMedia) {
            message = new Message(context.requestId(), currentUserId, MessageKind.IMAGE, content, request.mediaId());
        } else {
            message = new Message(context.requestId(), currentUserId, content);
        }
        if (aiDecision != null) {
            message.setAiConfidence(aiDecision.confidence());
        }
        Message saved = messageRepository.save(message);

        return MessageMapper.toResponse(saved, sharedCard);
    }

    /**
     * Lấy danh sách tin nhắn theo chu kỳ (polling), hỗ trợ lọc tin mới hơn afterId (FR-68).
     * Tự động ghép thẻ bài đăng cho các tin nhắn dạng ITEM_CARD.
     * Chỉ trả về các tin nhắn hợp lệ (moderation = PASS).
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

        return messages.stream()
                .filter(m -> m.getModeration() == MessageModeration.PASS)
                .map(m -> {
                    ItemCardDto card = null;
                    if (m.getRefItemId() != null) {
                        card = findItemCardSafely(m.getRefItemId());
                    }
                    return MessageMapper.toResponse(m, card);
                })
                .toList();
    }

    /**
     * Lấy danh sách thẻ sản phẩm ghim ở đầu cuộc trò chuyện (FR-69, BR-C02).
     * Nếu là bài Trao đổi thì ghim cả hai món (bài chính và món đề nghị).
     *
     * @param requestId     id của yêu cầu giao dịch
     * @param currentUserId id người đang thao tác
     * @return danh sách thẻ sản phẩm ghim ở đầu chat
     */
    @Transactional(readOnly = true)
    public List<ItemCardDto> getPinnedItems(long requestId, Long currentUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ChatErrorCode.CHAT_FORBIDDEN);
        }

        RequestChatContext context = validateAndGetChatContext(requestId, currentUserId);

        List<ItemCardDto> pinned = new ArrayList<>();
        if (itemQueryService != null) {
            if (context.itemId() != null) {
                ItemCardDto mainCard = findItemCardSafely(context.itemId());
                if (mainCard != null) {
                    pinned.add(mainCard);
                }
            }
            if (context.offeredItemId() != null) {
                ItemCardDto offeredCard = findItemCardSafely(context.offeredItemId());
                if (offeredCard != null) {
                    pinned.add(offeredCard);
                }
            }
        }
        return pinned;
    }

    private ItemCardDto findItemCardSafely(Long itemId) {
        if (itemQueryService == null || itemId == null) {
            return null;
        }
        try {
            return itemQueryService.getItemCard(itemId);
        } catch (Exception e) {
            return null;
        }
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

        // 1. Kiểm tra trạng thái: Khung chat xem được khi Request từ RESERVED trở lên hoặc CANCELLED
        if (!ALLOWED_READ_CHAT_STATUSES.contains(context.status())) {
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
