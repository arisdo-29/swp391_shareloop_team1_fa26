package com.shareloop.request.dto;

import java.time.Instant;

/**
 * Dữ liệu một giao dịch của riêng module request cho trang chi tiết của Admin. Các module khác bổ sung tên người, món
 * đồ, báo cáo và điểm.
 *
 * @param offeredItemId món đem đổi, null với giao dịch Cho tặng
 * @param deliveryMethod hình thức giao nhận, null khi chưa chọn
 * @param meetingPlace địa điểm hoặc địa chỉ giao nhận, null khi chưa có
 * @param cancelReason lý do huỷ kèm tiền tố người huỷ, null khi giao dịch không bị huỷ
 * @param updatedAt lần cập nhật cuối, dùng làm thời điểm huỷ hoặc không được chọn
 */
public record AdminRequestCore(
        long id,
        long itemId,
        long receiverId,
        Long offeredItemId,
        String type,
        String status,
        String deliveryMethod,
        String meetingPlace,
        Instant meetingTime,
        Instant donorWaiverAt,
        Instant receiverWaiverAt,
        Instant donorScheduleOkAt,
        Instant receiverScheduleOkAt,
        Instant contactRevealedAt,
        Instant donorHandoverAt,
        Instant receiverHandoverAt,
        boolean autoConfirmed,
        Instant reservedAt,
        Instant completedAt,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt) {}
