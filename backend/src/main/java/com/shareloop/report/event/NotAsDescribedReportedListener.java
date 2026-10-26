package com.shareloop.report.event;

import com.shareloop.report.service.ReportService;
import com.shareloop.request.event.NotAsDescribedReportedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Mở report NOT_AS_DESCRIBED sau khi bên nhận xác nhận giao nhận xong (FR-85, BR-R05). */
@Component
public class NotAsDescribedReportedListener {
    private final ReportService reportService;

    public NotAsDescribedReportedListener(ReportService reportService) {
        this.reportService = reportService;
    }

    // AFTER_COMMIT: chỉ chạy khi việc xác nhận giao nhận đã lưu thành công, nếu bước đó rollback thì không có report.
    // REQUIRES_NEW: lúc này transaction cũ đã đóng, muốn ghi report phải mở transaction mới.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNotAsDescribed(NotAsDescribedReportedEvent event) {
        reportService.openNotAsDescribedReport(event.receiverId(), event.requestId(), event.reason());
    }
}
