package com.shareloop.listingfee.dto;

import java.time.Instant;

/**
 * Kết quả một lần đẩy bài (C15, FR-36).
 *
 * @param itemId bài vừa đẩy
 * @param boostedUntil thời điểm hết đẩy mới của bài
 * @param creditBalance số dư Credit của chủ bài sau khi trừ phí đẩy bài
 */
public record BoostResponse(long itemId, Instant boostedUntil, long creditBalance) {}
