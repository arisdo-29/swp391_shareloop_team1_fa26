package com.shareloop.item.dto;

import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;

/**
 * DTO thẻ bài đăng dùng để hiển thị trong khung chat và ghim đầu chat (FR-69, FR-70; BR-C02, BR-C07).
 */
public record ItemCardDto(
        Long id, String title, OfferType offerType, ItemStatus status, String imageUrl, Long donorId) {}
