package com.shareloop.request.dto;

import java.time.Instant;

/**
 * Tiến độ giao hàng hai chiều (GN-12), gộp vào RequestResponse bằng JsonUnwrapped nên JSON vẫn phẳng. Địa chỉ của bên
 * kia chỉ có sau khi chốt lịch. Chiều 1 là món chính gửi cho người nhận (người gửi là chủ bài), chiều 2 là món đổi
 * gửi cho chủ bài (người gửi là người nhận).
 */
public record ShippingInfo(
        String receiverAddress,
        String donorAddress,
        Instant donorShippedAt,
        Instant receiverShippedAt,
        // Đã nhập địa chỉ hay chưa, luôn trả đúng kể cả khi nội dung địa chỉ của bên kia bị ẩn trước khi chốt lịch.
        Boolean receiverAddressProvided,
        Boolean donorAddressProvided,
        Instant exchangeShippingWindowStart,
        Short exchangeShippingUnsentLeg,
        Instant donorItemReopenAt,
        Instant receiverItemReopenAt) {

    public ShippingInfo(
            String receiverAddress,
            String donorAddress,
            Instant donorShippedAt,
            Instant receiverShippedAt,
            Boolean receiverAddressProvided,
            Boolean donorAddressProvided) {
        this(
                receiverAddress,
                donorAddress,
                donorShippedAt,
                receiverShippedAt,
                receiverAddressProvided,
                donorAddressProvided,
                null,
                null,
                null,
                null);
    }

    public static final ShippingInfo EMPTY = new ShippingInfo(null, null, null, null, null, null);
}
