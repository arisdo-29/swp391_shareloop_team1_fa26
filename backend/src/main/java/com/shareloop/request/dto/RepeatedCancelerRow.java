package com.shareloop.request.dto;

import java.time.Instant;

/** Một người đề nghị huỷ nhiều giao dịch sau khi đã thấy liên hệ (GN-06), cho màn Giám sát bất thường của Admin. */
public record RepeatedCancelerRow(Long userId, long cancelCount, Instant lastCancelledAt) {}
