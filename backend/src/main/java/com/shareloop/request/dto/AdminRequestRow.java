package com.shareloop.request.dto;

import java.time.Instant;

/**
 * Một dòng trong danh sách giao dịch của Admin.
 *
 * @param flagReason lý do gắn cờ "Bị kẹt" bằng tiếng Việt; null khi giao dịch không bị kẹt
 */
public record AdminRequestRow(
        Long id,
        Long itemId,
        String itemTitle,
        String type,
        String status,
        Long donorId,
        String donorName,
        Long receiverId,
        String receiverName,
        Instant meetingTime,
        Instant createdAt,
        String flagReason) {}
