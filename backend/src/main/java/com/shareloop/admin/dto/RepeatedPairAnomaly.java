package com.shareloop.admin.dto;

import java.time.Instant;

/**
 * Cặp người dùng giao dịch với nhau nhiều bất thường (FR-113, BR-U08).
 *
 * @param userA người có id nhỏ hơn trong cặp
 * @param userB người có id lớn hơn trong cặp
 * @param completedCount số giao dịch COMPLETED giữa hai người trong cửa sổ, tính cả hai chiều cho và nhận
 * @param lastCompletedAt thời điểm hoàn tất gần nhất của cặp
 */
public record RepeatedPairAnomaly(AnomalyUser userA, AnomalyUser userB, long completedCount, Instant lastCompletedAt) {}
