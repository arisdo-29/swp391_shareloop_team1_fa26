package com.shareloop.reputation.dto;

import com.shareloop.reputation.entity.Rank;

/**
 * Tóm tắt thông tin tín nhiệm và uy tín của người dùng (YC-06, UT-06).
 *
 * @param userId id của người dùng
 * @param trustStars số sao uy tín (0 - 5 sao, mặc định 5)
 * @param rank hạng tín nhiệm (BRONZE, SILVER, GOLD, DIAMOND)
 */
public record ReputationSummary(Long userId, Integer trustStars, Rank rank) {}
