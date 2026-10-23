package com.shareloop.reputation.dto;

import java.time.Instant;

/**
 * Một cặp người dùng giao dịch với nhau nhiều bất thường (FR-113, BR-U08).
 *
 * @param userAId id nhỏ hơn trong cặp
 * @param userBId id lớn hơn trong cặp
 * @param completedCount số giao dịch COMPLETED giữa hai người trong cửa sổ, tính cả hai chiều
 * @param lastCompletedAt thời điểm hoàn tất gần nhất của cặp
 */
public record RepeatedPair(Long userAId, Long userBId, long completedCount, Instant lastCompletedAt) {}
