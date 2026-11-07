package com.shareloop.chat.service;

import com.shareloop.chat.ChatErrorCode;
import com.shareloop.chat.dto.MessageResponse;
import com.shareloop.chat.dto.ProposeOfferChangeRequest;
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
import com.shareloop.notification.service.NotificationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.RequestChatContext;
import com.shareloop.request.dto.RequestResponse;
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
    private final NotificationService notificationService;

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

    // Dạng 6 tham số (chưa có NotificationService) để code và test đang gọi vẫn compile.
    public ChatService(
            MessageRepository messageRepository,
            RequestService requestService,
            ChatContactFilter chatContactFilter,
            MediaService mediaService,
            ItemQueryService itemQueryService,
            ChatViolationRecorder chatViolationRecorder) {
        this(
                messageRepository,
                requestService,
                chatContactFilter,
                mediaService,
                itemQueryService,
                chatViolationRecorder,
                null);
    }

    @Autowired
    public ChatService(
            MessageRepository messageRepository,
            RequestService requestService,
            ChatContactFilter chatContactFilter,
            MediaService mediaService,
            @Autowired(required = false) ItemQueryService itemQueryService,
            @Autowired(required = false) ChatViolationRecorder chatViolationRecorder,
            @Autowired(required = false) NotificationService notificationService) {
        this.messageRepository = messageRepository;
        this.requestService = requestService;
        this.chatContactFilter = chatContactFilter;
        this.mediaService = mediaService;
        this.itemQueryService = itemQueryService;
        this.chatViolationRecorder = chatViolationRecorder;
        this.notificationService = notificationService;
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

        if (hasContent) {
            rejectIfContentViolates(context, currentUserId, request.content());
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

    /**
     * Người nhận đề nghị đưa một món của mình vào đổi trong giao dịch Cho đã chọn người (Cho → Trao đổi). Đề nghị cũ
     * còn PENDING tự chuyển DECLINED vì mỗi giao dịch chỉ có một đề nghị chờ trả lời; chủ bài được thông báo.
     *
     * @throws BusinessException CHAT_FORBIDDEN nếu người gửi không phải người nhận, CHAT_MESSAGE_BLOCKED nếu lời nhắn
     *     vi phạm, các mã REQUEST_* nếu giao dịch hoặc món không hợp lệ (xem RequestService.validateOfferChangeProposal)
     */
    @Transactional
    public MessageResponse proposeOfferChange(long requestId, Long currentUserId, ProposeOfferChangeRequest request) {
        RequestChatContext context = requireChatParty(requestId, currentUserId);
        if (!currentUserId.equals(context.receiverId())) {
            throw new BusinessException(ChatErrorCode.CHAT_FORBIDDEN);
        }
        requestService.validateOfferChangeProposal(requestId, request.offeredItemId());

        String content = request.content() != null && !request.content().isBlank()
                ? request.content().trim()
                : null;
        if (content != null) {
            rejectIfContentViolates(context, currentUserId, content);
        }

        declinePendingOffers(requestId);
        Message saved =
                messageRepository.save(Message.offerChange(requestId, currentUserId, request.offeredItemId(), content));

        notify(
                context.donorId(),
                "OFFER_CHANGE_PROPOSED",
                "Có đề nghị đổi món",
                "Người nhận muốn đưa một món của mình vào trao đổi. Mở cuộc trò chuyện để xem và trả lời.",
                requestId);
        return MessageMapper.toResponse(saved, findItemCardSafely(saved.getRefItemId()));
    }

    /**
     * Chủ bài đồng ý đề nghị đổi món: giao dịch chuyển sang Trao đổi qua RequestService.acceptOfferedItemInChat (trừ
     * POST_FEE, giữ chỗ món), đề nghị thành ACCEPTED và thêm tin hệ thống. Cùng một transaction nên lỗi ở bước nào
     * (thiếu Credit, món đã bị giữ) thì đề nghị vẫn PENDING và tiền không bị trừ.
     *
     * @throws BusinessException CHAT_FORBIDDEN nếu không phải chủ bài, CHAT_OFFER_CHANGE_NOT_FOUND hoặc
     *     CHAT_OFFER_CHANGE_NOT_PENDING nếu tin không hợp lệ, các lỗi của acceptOfferedItemInChat
     */
    @Transactional
    public RequestResponse acceptOfferChange(long requestId, long messageId, Long currentUserId) {
        RequestChatContext context = requireChatParty(requestId, currentUserId);
        requireDonor(context, currentUserId);
        Message offer = findPendingOfferChange(requestId, messageId);

        RequestResponse response =
                requestService.acceptOfferedItemInChat(requestId, currentUserId, offer.getRefItemId());

        offer.accept();
        messageRepository.save(offer);
        messageRepository.save(Message.system(
                requestId,
                "Chủ bài đã đồng ý trao đổi với " + itemTitleOf(offer.getRefItemId())
                        + ". Giao dịch chuyển sang Trao đổi."));
        return response;
    }

    /**
     * Chủ bài từ chối đề nghị đổi món: đề nghị thành DECLINED, thêm tin hệ thống và báo người nhận. Không tốn phí.
     *
     * @throws BusinessException CHAT_FORBIDDEN nếu không phải chủ bài, CHAT_OFFER_CHANGE_NOT_FOUND hoặc
     *     CHAT_OFFER_CHANGE_NOT_PENDING nếu tin không hợp lệ
     */
    @Transactional
    public MessageResponse declineOfferChange(long requestId, long messageId, Long currentUserId) {
        RequestChatContext context = requireChatParty(requestId, currentUserId);
        requireDonor(context, currentUserId);
        Message offer = findPendingOfferChange(requestId, messageId);

        offer.decline();
        Message saved = messageRepository.save(offer);
        String itemTitle = itemTitleOf(offer.getRefItemId());
        messageRepository.save(Message.system(requestId, "Chủ bài chưa đồng ý đưa " + itemTitle + " vào trao đổi."));

        notify(
                context.receiverId(),
                "OFFER_CHANGE_DECLINED",
                "Đề nghị đổi món chưa được đồng ý",
                "Chủ bài chưa đồng ý đưa " + itemTitle + " vào trao đổi. Giao dịch vẫn là Cho.",
                requestId);
        return MessageMapper.toResponse(saved, findItemCardSafely(saved.getRefItemId()));
    }

    /**
     * Tự huỷ mọi đề nghị đổi món còn PENDING khi giao dịch rời giai đoạn thương lượng (đã chốt lịch hoặc bị huỷ). Có đề
     * nghị bị huỷ thì thêm một tin hệ thống ngắn; không gửi thông báo vì hai bên đã nhận thông báo chốt lịch hoặc huỷ.
     */
    @Transactional
    public void declinePendingOfferChanges(long requestId) {
        int declinedCount = declinePendingOffers(requestId);
        if (declinedCount > 0) {
            messageRepository.save(Message.system(
                    requestId,
                    "Đề nghị đổi món chưa được trả lời đã tự huỷ vì giao dịch đã chốt lịch hoặc đã bị huỷ."));
        }
    }

    // Chuyển mọi đề nghị đổi món PENDING của giao dịch sang DECLINED, trả về số đề nghị đã huỷ. Dùng khi có đề nghị mới
    // (mỗi giao dịch chỉ giữ tối đa một đề nghị PENDING) và khi giao dịch rời giai đoạn thương lượng.
    private int declinePendingOffers(long requestId) {
        List<Message> pendingOffers = messageRepository.findByRequestIdAndKindAndOfferStatus(
                requestId, MessageKind.OFFER_CHANGE, Message.OFFER_PENDING);
        for (Message pendingOffer : pendingOffers) {
            pendingOffer.decline();
            messageRepository.save(pendingOffer);
        }
        return pendingOffers.size();
    }

    // Khoá dòng tin đề nghị để hai lần bấm Đồng ý/Từ chối cùng lúc không cùng xử lý một đề nghị (tránh trừ phí hai
    // lần).
    private Message findPendingOfferChange(long requestId, long messageId) {
        Message offer = messageRepository
                .findByIdForUpdate(messageId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHAT_OFFER_CHANGE_NOT_FOUND));
        boolean belongsToRequest = offer.getRequestId() != null && offer.getRequestId() == requestId;
        if (!belongsToRequest || offer.getKind() != MessageKind.OFFER_CHANGE) {
            throw new BusinessException(ChatErrorCode.CHAT_OFFER_CHANGE_NOT_FOUND);
        }
        if (!offer.isPendingOfferChange()) {
            throw new BusinessException(ChatErrorCode.CHAT_OFFER_CHANGE_NOT_PENDING);
        }
        return offer;
    }

    // Chỉ chủ bài trả lời đề nghị đổi món vì chủ bài là người trả phí chuyển sang Trao đổi.
    private static void requireDonor(RequestChatContext context, Long currentUserId) {
        if (!currentUserId.equals(context.donorId())) {
            throw new BusinessException(ChatErrorCode.CHAT_FORBIDDEN);
        }
    }

    private RequestChatContext requireChatParty(long requestId, Long currentUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ChatErrorCode.CHAT_FORBIDDEN);
        }
        return validateAndGetChatContext(requestId, currentUserId);
    }

    // Tên món để ghép vào tin hệ thống; không tra được thì dùng chữ chung để tin nhắn vẫn đọc được.
    private String itemTitleOf(Long itemId) {
        ItemCardDto card = findItemCardSafely(itemId);
        return card != null && card.title() != null ? card.title() : "món đề nghị";
    }

    // Link /requests/{id} mở đúng cuộc trò chuyện của giao dịch ở frontend.
    private void notify(Long userId, String type, String title, String body, long requestId) {
        if (notificationService == null || userId == null) {
            return;
        }
        notificationService.notify(userId, type, title, body, "/requests/" + requestId);
    }

    // Lọc thông tin liên hệ và từ khoá cấm mức BLOCK (FR-72, FR-74, FR-78; BR-C01, BR-A10, BR-C06). Dùng chung cho tin
    // văn bản và lời nhắn kèm đề nghị đổi món để hai nơi không lệch luật lọc.
    private void rejectIfContentViolates(RequestChatContext context, Long senderId, String content) {
        boolean isBeforeLogistics =
                context.status() == RequestStatus.RESERVED || context.status() == RequestStatus.AWAITING_LOGISTICS;
        Optional<String> violation = chatContactFilter.checkViolation(content, isBeforeLogistics);
        if (violation.isPresent()) {
            String reason = violation.get();
            recordViolation(context, senderId, content, reason);
            throw new BusinessException(ChatErrorCode.CHAT_MESSAGE_BLOCKED, "Tin nhắn bị chặn do chứa " + reason + ".");
        }
        if (!isBeforeLogistics) {
            return;
        }
        try {
            chatContactFilter.validateMessage(content);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ChatErrorCode.CHAT_MESSAGE_BLOCKED) {
                recordViolation(context, senderId, content, e.getMessage());
            }
            throw e;
        }
    }

    private void recordViolation(RequestChatContext context, Long senderId, String content, String reason) {
        if (chatViolationRecorder != null) {
            chatViolationRecorder.recordViolationAndAlertAdmin(context.requestId(), senderId, content.trim(), reason);
        }
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
