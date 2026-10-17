package com.shareloop.request.dto;

import java.time.Instant;

/**
 * DTO phản hồi thông tin của một yêu cầu giao dịch (F10, UC-26, UC-27).
 *
 * <p>receiverName chỉ là họ tên người xin, có thể null; email và số điện thoại chỉ trao khi chốt lịch
 * (BR-T11) nên không nằm ở đây.
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
        Instant donorHandoverAt,
        Instant receiverHandoverAt,
        Boolean asDescribed,
        Instant completedAt,
        Instant createdAt,
        String receiverName,
        Integer receiverTrustStars,
        String receiverRank,
        Double receiverAsDescribedRate) {

    // Getter tiện ích để tương thích với các client đọc theo tên ngắn (FR-65, Issue #110)
    public Integer trustStars() {
        return receiverTrustStars;
    }

    public String rank() {
        return receiverRank;
    }

    public Double asDescribedRate() {
        return receiverAsDescribedRate;
    }

    // Giữ dạng 20 tham số để tương thích code đang gọi toResponse(request, receiverName)
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
            Instant donorScheduleOkAt,
            Instant receiverScheduleOkAt,
            Instant donorWaiverAt,
            Instant receiverWaiverAt,
            Instant contactRevealedAt,
            Instant donorHandoverAt,
            Instant receiverHandoverAt,
            Boolean asDescribed,
            Instant completedAt,
            Instant createdAt,
            String receiverName) {
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
                donorScheduleOkAt,
                receiverScheduleOkAt,
                donorWaiverAt,
                receiverWaiverAt,
                contactRevealedAt,
                donorHandoverAt,
                receiverHandoverAt,
                asDescribed,
                completedAt,
                createdAt,
                receiverName,
                null,
                null,
                null);
    }

    // Giữ dạng 19 tham số cũ để code và test đang dùng vẫn compile; receiverName = null.
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
            Instant donorScheduleOkAt,
            Instant receiverScheduleOkAt,
            Instant donorWaiverAt,
            Instant receiverWaiverAt,
            Instant contactRevealedAt,
            Instant donorHandoverAt,
            Instant receiverHandoverAt,
            Boolean asDescribed,
            Instant completedAt,
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
                donorScheduleOkAt,
                receiverScheduleOkAt,
                donorWaiverAt,
                receiverWaiverAt,
                contactRevealedAt,
                donorHandoverAt,
                receiverHandoverAt,
                asDescribed,
                completedAt,
                createdAt,
                null);
    }

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
            Instant donorScheduleOkAt,
            Instant receiverScheduleOkAt,
            Instant donorWaiverAt,
            Instant receiverWaiverAt,
            Instant contactRevealedAt,
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
                donorScheduleOkAt,
                receiverScheduleOkAt,
                donorWaiverAt,
                receiverWaiverAt,
                contactRevealedAt,
                null,
                null,
                null,
                null,
                createdAt,
                null);
    }

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
                null,
                null,
                null,
                null,
                createdAt,
                null);
    }
}
