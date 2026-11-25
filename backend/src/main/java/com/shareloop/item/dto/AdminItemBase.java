package com.shareloop.item.dto;

import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import java.time.Instant;

/**
 * Dữ liệu một bài đăng của riêng module item cho danh sách Admin; các module khác bổ sung phần còn lại.
 *
 * @param active false khi bài đang bị ẩn
 * @param hiddenByAdmin true khi bài do Admin ẩn
 * @param imageUrl ảnh đầu tiên, null khi chưa có
 * @param approvedAt lúc duyệt gần nhất, null khi chưa từng được duyệt
 */
public record AdminItemBase(
        long id,
        String title,
        long donorId,
        Long categoryId,
        OfferType offerType,
        ItemStatus status,
        boolean active,
        boolean hiddenByAdmin,
        Instant createdAt,
        Instant reviewSubmittedAt,
        Instant approvedAt,
        String imageUrl) {}
