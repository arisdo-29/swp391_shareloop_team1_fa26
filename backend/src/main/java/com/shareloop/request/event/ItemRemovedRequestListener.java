package com.shareloop.request.event;

import com.shareloop.item.event.ItemRemovedEvent;
import com.shareloop.request.service.RequestService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Huỷ các yêu cầu PENDING khi bài đăng bị gỡ (D-13, BD-11). */
@Component
public class ItemRemovedRequestListener {

    private final RequestService requestService;

    public ItemRemovedRequestListener(RequestService requestService) {
        this.requestService = requestService;
    }

    @EventListener
    public void onItemRemoved(ItemRemovedEvent event) {
        requestService.cancelPendingRequestsForItemRemoved(event.itemId());
    }
}
