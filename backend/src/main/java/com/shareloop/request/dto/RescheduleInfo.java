package com.shareloop.request.dto;

import java.time.Instant;

/**
 * Đề nghị dời lịch đang chờ bên kia phản hồi và số lần đã dời (GN-11), gộp vào RequestResponse bằng JsonUnwrapped.
 *
 * @param rescheduleProposedBy id người đề nghị dời, null khi không có đề nghị đang chờ
 * @param rescheduleCount số lần dời lịch đã được đồng ý
 */
public record RescheduleInfo(
        Long rescheduleProposedBy,
        Instant rescheduleProposedAt,
        Instant rescheduleMeetingTime,
        String rescheduleMeetingPlace,
        Integer rescheduleCount) {

    public static final RescheduleInfo EMPTY = new RescheduleInfo(null, null, null, null, null);
}
