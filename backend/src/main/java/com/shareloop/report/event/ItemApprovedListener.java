package com.shareloop.report.event;

import com.shareloop.item.event.ItemApprovedEvent;
import com.shareloop.report.service.ReportService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Hiện lại bài bị ẩn vì báo cáo vi phạm khi chủ bài đã sửa và được Admin duyệt lại. */
@Component
public class ItemApprovedListener {
    private final ReportService reportService;

    public ItemApprovedListener(ReportService reportService) {
        this.reportService = reportService;
    }

    // @EventListener thường: chạy cùng transaction với lúc duyệt, nên bài hiện lại hay không cùng thành công hoặc cùng
    // rollback với việc duyệt.
    @EventListener
    public void onItemApproved(ItemApprovedEvent event) {
        reportService.relistItemAfterReapproval(event.itemId());
    }
}
