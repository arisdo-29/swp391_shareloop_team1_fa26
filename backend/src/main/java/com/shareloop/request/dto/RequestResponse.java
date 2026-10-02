package com.shareloop.request.dto;

import java.time.Instant;

/**
 * DTO phản hồi thông tin của một yêu cầu giao dịch (F10).
 */
public record RequestResponse(
        Long id,
        Long itemId,
        Long receiverId,
        Long offeredItemId,
        String type,
        String status,
        String deliveryMethod,
        String meetingPlace,
        Instant meetingTime,
        Instant createdAt) {}
