package com.shareloop.request.event;

import com.shareloop.item.event.ItemUnavailableEvent;
import com.shareloop.request.service.RequestService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Bài không còn dùng được (giữ chỗ, xoá, ẩn, sửa, hết hạn): huỷ các yêu cầu PENDING đang đem bài này đi đổi (YC-04,
 * YC-07). Bài đang hiển thị bị sửa thì báo thêm cho người đang xin bài.
 */
@Component
public class ItemUnavailableRequestListener {

    private final RequestService requestService;

    public ItemUnavailableRequestListener(RequestService requestService) {
        this.requestService = requestService;
    }

    // @EventListener thường: chạy cùng transaction với thao tác trên bài, lỗi thì cả thao tác đó cũng rollback.
    @EventListener
    public void onItemUnavailable(ItemUnavailableEvent event) {
        requestService.cancelPendingRequestsOfferingItem(event.itemId());
        // Bài hết hạn, bị ẩn hoặc bản sửa bị từ chối: người đang xin bài không còn chọn được nên huỷ ngay (C1).
        requestService.cancelPendingRequestsToItem(event.itemId(), event.reason());
        if (event.reason() == ItemUnavailableEvent.Reason.EDITED) {
            requestService.notifyPendingRequestersItemEdited(event.itemId());
        }
    }
}
