package com.shareloop.chat.event;

import com.shareloop.chat.service.ChatService;
import com.shareloop.request.event.RequestNegotiationEndedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Tự huỷ đề nghị đổi món còn PENDING khi giao dịch đã chốt lịch hoặc bị huỷ. */
@Component
public class RequestNegotiationEndedListener {
    private final ChatService chatService;

    public RequestNegotiationEndedListener(ChatService chatService) {
        this.chatService = chatService;
    }

    // @EventListener thường (không phải AFTER_COMMIT): chạy cùng transaction với lúc Request đổi trạng thái, để không
    // còn khoảnh khắc nào chủ bài bấm Đồng ý được một đề nghị của giao dịch đã chốt lịch hoặc đã huỷ.
    @EventListener
    public void onNegotiationEnded(RequestNegotiationEndedEvent event) {
        chatService.declinePendingOfferChanges(event.requestId());
    }
}
