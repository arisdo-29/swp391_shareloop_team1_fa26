package com.shareloop.chat.event;

import com.shareloop.chat.service.ChatService;
import com.shareloop.report.event.DisputeOpenedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Thêm dòng tin hệ thống vào khung chat khi giao dịch bị khiếu nại (D-04). */
@Component
public class DisputeOpenedListener {
    private final ChatService chatService;

    public DisputeOpenedListener(ChatService chatService) {
        this.chatService = chatService;
    }

    // AFTER_COMMIT: chỉ ghi tin khi khiếu nại đã lưu thành công. Tin hệ thống không quan trọng bằng khiếu nại, nên lỗi
    // ghi tin không được làm hỏng việc mở khiếu nại.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDisputeOpened(DisputeOpenedEvent event) {
        chatService.postDisputeOpenedNotice(event.requestId(), event.responseHours());
    }
}
