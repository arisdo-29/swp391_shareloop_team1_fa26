package com.shareloop.item.dto;

import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import java.time.Instant;
import java.util.List;

/**
 * Bộ lọc danh sách bài đăng của Admin. Trường null là không lọc theo tiêu chí đó.
 *
 * @param active true chỉ bài đang hiện, false chỉ bài đang ẩn
 * @param ownerId chỉ bài của người này
 * @param keyword khớp một phần tiêu đề (không phân biệt hoa thường), hoặc bài của người trong keywordOwnerIds
 * @param keywordOwnerIds id người có tên hoặc email khớp từ khoá (module khác tra giúp)
 * @param from, to khoảng ngày tạo bài [from, to)
 * @param boostedIds chỉ các bài có id trong danh sách (bài đang được đẩy); null là không lọc
 */
public record AdminItemQuery(
        ItemStatus status,
        Boolean active,
        OfferType offerType,
        Long categoryId,
        Long ownerId,
        String keyword,
        List<Long> keywordOwnerIds,
        Instant from,
        Instant to,
        List<Long> boostedIds) {}
