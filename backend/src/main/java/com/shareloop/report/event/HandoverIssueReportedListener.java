package com.shareloop.report.event;

import com.shareloop.report.service.ReportService;
import com.shareloop.request.event.HandoverIssueReportedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Mở khiếu nại NO_SHOW khi một bên báo chưa nhận được hoặc chưa giao được đồ. */
@Component
public class HandoverIssueReportedListener {
    private final ReportService reportService;

    public HandoverIssueReportedListener(ReportService reportService) {
        this.reportService = reportService;
    }

    // @EventListener thường (không phải AFTER_COMMIT): chạy cùng transaction với lúc Request sang DISPUTED. Mở khiếu
    // nại lỗi thì cả hai cùng rollback, tránh giao dịch DISPUTED mà không có report nào để Admin xử lý.
    @EventListener
    public void onHandoverIssue(HandoverIssueReportedEvent event) {
        reportService.openHandoverIssueReport(
                event.reporterId(), event.requestId(), event.note(), event.evidenceMediaIds());
    }
}
