package com.shareloop.report.event;

import com.shareloop.report.service.ReportService;
import com.shareloop.request.event.CancelRequestRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Mở khiếu nại khi bên kia không đồng ý đề nghị huỷ giao dịch (GN-06). */
@Component
public class CancelRequestRejectedListener {
    private final ReportService reportService;

    public CancelRequestRejectedListener(ReportService reportService) {
        this.reportService = reportService;
    }

    // @EventListener thường: chạy cùng transaction với lúc giao dịch sang DISPUTED, lỗi thì cùng rollback.
    @EventListener
    public void onCancelRequestRejected(CancelRequestRejectedEvent event) {
        reportService.openCancelRejectedDispute(event.reporterId(), event.requestId(), event.note());
    }
}
