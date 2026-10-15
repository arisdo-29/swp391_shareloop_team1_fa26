package com.shareloop.listingfee.dto;

import java.time.Instant;

/**
 * Kết quả một lần gia hạn bài (F08 bước 1, FR-28).
 *
 * @param itemId bài vừa gia hạn
 * @param fee số Credit vừa trừ (renew_fee lúc gia hạn); 0 khi Admin cấu hình miễn phí
 * @param expireAt hạn hiển thị mới của bài
 */
public record RenewResponse(long itemId, int fee, Instant expireAt) {}
