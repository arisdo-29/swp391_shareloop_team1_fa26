package com.shareloop.item.dto;

import java.time.Instant;
import java.util.List;

/**
 * Banner bài đẩy ở trang chủ (GET /api/v1/items/featured): một nhóm bài đang đẩy theo vòng xoay của lượt hiện tại.
 *
 * @param items các bài trong nhóm, cùng dạng ItemResponse với trang Tìm đồ (không có các mốc đợt đẩy)
 * @param rotatedAt lúc bắt đầu lượt hiện tại = round * boost_rotate_seconds
 * @param nextRotateAt lúc đổi sang nhóm kế tiếp = (round + 1) * boost_rotate_seconds; FE tải lại banner lúc này
 */
public record FeaturedItemsResponse(List<ItemResponse> items, Instant rotatedAt, Instant nextRotateAt) {}
