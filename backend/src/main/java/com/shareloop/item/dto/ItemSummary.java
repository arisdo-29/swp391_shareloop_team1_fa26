package com.shareloop.item.dto;

import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;

/** Minimal item data shared with the request module. */
public record ItemSummary(Long id, Long donorId, OfferType offerType, ItemStatus status, boolean active) {

    public ItemSummary(Long id, Long donorId, OfferType offerType, ItemStatus status) {
        this(id, donorId, offerType, status, true);
    }
}
