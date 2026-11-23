package com.shareloop.report.event;

import com.shareloop.report.service.ReportService;
import com.shareloop.request.event.SilentCancelRequestReportedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Mở báo cáo không đến hẹn khi đề nghị huỷ hết hạn mà bên kia im lặng (GN-06). */
@Component
public class SilentCancelRequestReportedListener {
    private final ReportService reportService;

    public SilentCancelRequestReportedListener(ReportService reportService) {
        this.reportService = reportService;
    }

    @EventListener
    public void onSilentCancel(SilentCancelRequestReportedEvent event) {
        reportService.openSilentCancelReport(event.reporterId(), event.requestId(), event.issuePhrase(), event.note());
    }
}
