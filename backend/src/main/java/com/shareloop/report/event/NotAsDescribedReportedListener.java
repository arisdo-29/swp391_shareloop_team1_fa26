package com.shareloop.report.event;

import com.shareloop.report.service.ReportService;
import com.shareloop.request.event.NotAsDescribedReportedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Mở report NOT_AS_DESCRIBED khi bên nhận báo không đúng mô tả (FR-85, BR-R05, D-04). */
@Component
public class NotAsDescribedReportedListener {
    private final ReportService reportService;

    public NotAsDescribedReportedListener(ReportService reportService) {
        this.reportService = reportService;
    }

    // @EventListener thường (không phải AFTER_COMMIT): chạy cùng transaction với lúc Request sang DISPUTED.
    // Mở khiếu nại lỗi thì cả hai cùng rollback, tránh giao dịch DISPUTED mà không có report để Admin xử lý.
    @EventListener
    public void onNotAsDescribed(NotAsDescribedReportedEvent event) {
        reportService.openNotAsDescribedReport(event.receiverId(), event.requestId(), event.reason());
    }
}
