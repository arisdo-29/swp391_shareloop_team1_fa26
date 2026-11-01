package com.shareloop.request.event;

import com.shareloop.item.event.ItemOfferTypeChangedEvent;
import com.shareloop.request.service.RequestService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Huỷ các yêu cầu cũ trước khi transaction đổi hình thức bài được commit. */
@Component
public class ItemOfferTypeChangedRequestListener {

    private final RequestService requestService;

    public ItemOfferTypeChangedRequestListener(RequestService requestService) {
        this.requestService = requestService;
    }

    @EventListener
    public void onItemOfferTypeChanged(ItemOfferTypeChangedEvent event) {
        requestService.cancelPendingRequestsForOfferTypeChange(event.itemId());
    }
}
