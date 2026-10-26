package com.shareloop.reputation.dto;

import com.shareloop.reputation.entity.Rank;

/**
 * Tóm tắt thông tin tín nhiệm và uy tín của người dùng (FR-65, FR-100).
 *
 * @param userId id của người dùng
 * @param trustStars số sao uy tín (0 - 5 sao, mặc định 5)
 * @param rank hạng tín nhiệm (BRONZE, SILVER, GOLD, DIAMOND)
 * @param asDescribedRate tỉ lệ đúng mô tả thang 0–1 (0.92 là 92%), null khi dưới 3 giao dịch đã được đánh giá
 */
public record ReputationSummary(Long userId, Integer trustStars, Rank rank, Double asDescribedRate) {}
