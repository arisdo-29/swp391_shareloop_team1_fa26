package com.shareloop.admin.dto;

import java.time.Instant;

/**
 * Người đề nghị huỷ nhiều giao dịch sau khi đã thấy thông tin liên hệ (GN-06). Chỉ để Admin xem xét, không tự phạt.
 *
 * @param user người đề nghị huỷ
 * @param cancelCount số giao dịch đã huỷ vì đề nghị của người này trong cửa sổ theo dõi
 * @param lastCancelRequestedAt lần đề nghị huỷ gần nhất
 */
public record RepeatedCancelerAnomaly(AnomalyUser user, long cancelCount, Instant lastCancelRequestedAt) {}
