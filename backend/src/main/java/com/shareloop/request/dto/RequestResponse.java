package com.shareloop.request.dto;

import java.time.Instant;

/**
 * DTO phản hồi thông tin của một yêu cầu giao dịch (F10, UC-26).
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
        Instant donorScheduleOkAt,
        Instant receiverScheduleOkAt,
        Instant donorWaiverAt,
        Instant receiverWaiverAt,
        Instant contactRevealedAt,
        Instant createdAt) {

    public RequestResponse(
            Long id,
            Long itemId,
            Long receiverId,
            Long offeredItemId,
            String type,
            String status,
            String deliveryMethod,
            String meetingPlace,
            Instant meetingTime,
            Instant createdAt) {
        this(
                id,
                itemId,
                receiverId,
                offeredItemId,
                type,
                status,
                deliveryMethod,
                meetingPlace,
                meetingTime,
                null,
                null,
                null,
                null,
                null,
                createdAt);
    }
}
