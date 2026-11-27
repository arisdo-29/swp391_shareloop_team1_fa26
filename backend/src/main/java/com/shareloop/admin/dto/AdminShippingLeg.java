package com.shareloop.admin.dto;

import java.time.Instant;

/**
 * Một chiều giao hàng nhìn từ Admin (GN-12). Chiều 1 là món chính chủ bài gửi, chiều 2 là món đổi người nhận gửi.
 *
 * @param step "Chờ gửi", "Đã gửi" hoặc "Đã nhận"
 * @param address địa chỉ nhận hàng của bên nhận chiều này, Admin thấy đủ ở mọi trạng thái
 */
public record AdminShippingLeg(
        int leg,
        String itemTitle,
        String senderRole,
        String receiverRole,
        String address,
        Instant shippedAt,
        Instant receivedAt,
        String step) {}
