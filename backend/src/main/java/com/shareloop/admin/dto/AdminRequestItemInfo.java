package com.shareloop.admin.dto;

import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;

/** Một món đồ trong giao dịch: bài chính hoặc món đem đổi. Bài đã xoá thì chỉ còn itemId. */
public record AdminRequestItemInfo(
        Long itemId, String title, String imageUrl, ItemStatus status, OfferType offerType) {}
