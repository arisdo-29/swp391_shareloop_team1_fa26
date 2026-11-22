package com.shareloop.listingfee.dto;

import java.time.Duration;
import java.time.Instant;

/**
 * Kết quả trả lại thời gian hiển thị khi bài được nhả sau giao dịch huỷ (B2).
 *
 * @param added số thời gian đã cộng vào hạn hiển thị
 * @param newExpireAt hạn hiển thị mới
 * @param graceApplied true khi sau khi cộng bài vẫn quá hạn nên hạn mới được đặt bằng lúc nhả cộng thời gian ân hạn
 */
public record DisplayRefund(Duration added, Instant newExpireAt, boolean graceApplied) {}
