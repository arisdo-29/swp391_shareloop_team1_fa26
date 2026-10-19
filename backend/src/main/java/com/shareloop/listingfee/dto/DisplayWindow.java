package com.shareloop.listingfee.dto;

import java.time.Instant;

/**
 * Hạn hiển thị và hạn đẩy của một bài (FR-29, FR-36), để module item trả kèm trong chi tiết và danh sách bài.
 *
 * @param expireAt hạn hiển thị; null khi bài chưa từng được duyệt
 * @param boostedUntil hạn đẩy bài; null khi bài chưa từng được đẩy
 */
public record DisplayWindow(Instant expireAt, Instant boostedUntil) {}
