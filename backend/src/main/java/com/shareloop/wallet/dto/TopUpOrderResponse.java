package com.shareloop.wallet.dto;

import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;

/**
 * Đơn nạp vừa tạo.
 *
 * @param orderCode mã đơn, dùng cho bước xác nhận
 * @param amountVnd số tiền nạp (VNĐ)
 * @param credits số Credit sẽ được cộng khi thanh toán xong
 * @param status trạng thái đơn (vừa tạo luôn là PENDING)
 * @param provider cổng thanh toán
 * @param redirectUrl địa chỉ FE chuyển người dùng tới để thanh toán
 */
public record TopUpOrderResponse(
        String orderCode,
        long amountVnd,
        int credits,
        PaymentOrderStatus status,
        PaymentProvider provider,
        String redirectUrl) {}
