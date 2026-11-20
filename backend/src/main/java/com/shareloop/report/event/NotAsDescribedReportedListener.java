package com.shareloop.report.event;

import com.shareloop.report.service.ReportService;
import com.shareloop.request.event.NotAsDescribedReportedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Mở báo cáo vi phạm "Không đúng mô tả" khi một bên từ chối nhận món đồ (GN-07, BC-09). */
@Component
public class NotAsDescribedReportedListener {
    private final ReportService reportService;

    public NotAsDescribedReportedListener(ReportService reportService) {
        this.reportService = reportService;
    }

    // @EventListener thường (không phải AFTER_COMMIT): chạy cùng transaction với lúc giao dịch bị huỷ.
    // Mở báo cáo lỗi thì cả hai cùng rollback, tránh món bị ẩn mà không có báo cáo để Admin xem xét.
    @EventListener
    public void onNotAsDescribed(NotAsDescribedReportedEvent event) {
        reportService.openRefusalReport(
                event.reporterId(),
                event.requestId(),
                event.refusedItemId(),
                event.reportedUserId(),
                event.reason(),
                event.evidenceMediaIds());
    }
}
