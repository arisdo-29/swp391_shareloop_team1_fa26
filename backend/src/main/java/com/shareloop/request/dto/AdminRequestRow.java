package com.shareloop.request.dto;

import java.time.Instant;
import java.util.List;

/**
 * Một dòng trong danh sách giao dịch của Admin.
 *
 * @param flagReason lý do gắn cờ "Bị kẹt" bằng tiếng Việt; null khi giao dịch không bị kẹt
 * @param deliveryMethod hình thức giao nhận; null khi chưa chọn
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
        String flagReason,
        String deliveryMethod,
        // Nhãn tiếng Việt cho danh sách: "Đang chờ phản hồi đề nghị huỷ", "Chờ gửi hàng", "Một bên đã nhận", "Quá hạn
        // gửi hàng"
        List<String> labels) {

    // Dạng 13 tham số (chưa có nhãn) để code và test đang gọi vẫn compile.
    public AdminRequestRow(
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
            String flagReason,
            String deliveryMethod) {
        this(
                id,
                itemId,
                itemTitle,
                type,
                status,
                donorId,
                donorName,
                receiverId,
                receiverName,
                meetingTime,
                createdAt,
                flagReason,
                deliveryMethod,
                List.of());
    }
}
