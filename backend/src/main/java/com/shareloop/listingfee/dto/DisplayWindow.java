package com.shareloop.listingfee.dto;

import java.time.Instant;

/**
 * Hạn hiển thị và hạn đẩy của một bài (BD-12, TD-01, HT-04), để module item trả kèm trong chi tiết và danh sách bài. Các mốc
 * của đợt đẩy do backend tính sẵn để FE chỉ hiển thị.
 *
 * @param expireAt hạn hiển thị; null khi bài chưa từng được duyệt
 * @param boostedUntil hạn đẩy bài; null khi bài chưa từng được đẩy
 * @param boostStartedAt mốc bắt đầu đợt đẩy gần nhất; null khi bài chưa từng được đẩy
 * @param boostChainEndsAt mốc đẩy muộn nhất của đợt đó = boostStartedAt + boost_max_days; null khi chưa từng đẩy
 * @param boostCooldownUntil mốc được mở đợt đẩy mới = boostedUntil + boost_cooldown_days; null khi chưa từng đẩy
 */
public record DisplayWindow(
        Instant expireAt,
        Instant boostedUntil,
        Instant boostStartedAt,
        Instant boostChainEndsAt,
        Instant boostCooldownUntil) {

    // Giữ constructor hai tham số cũ để code đang tạo DisplayWindow (test của module item) không phải sửa.
    public DisplayWindow(Instant expireAt, Instant boostedUntil) {
        this(expireAt, boostedUntil, null, null, null);
    }
}
