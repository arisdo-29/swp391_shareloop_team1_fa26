package com.shareloop.wallet.dto;

import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import java.time.Instant;

/**
 * Đơn nạp vừa tạo.
 *
 * @param orderCode mã đơn, dùng cho bước xác nhận
 * @param amountVnd số tiền nạp (VNĐ)
 * @param credits số Credit sẽ được cộng khi thanh toán xong
 * @param status trạng thái đơn (vừa tạo luôn là PENDING)
 * @param provider cổng thanh toán
 * @param redirectUrl địa chỉ FE chuyển người dùng tới để thanh toán
 * @param expiresAt hạn thanh toán = created_at + payment_order_expire_minutes (B29: FE đếm ngược theo mốc này, không tự
 *     tính); quá mốc này job chuyển đơn sang EXPIRED. Cùng mốc với vnp_ExpireDate gửi VNPay
 * @param serverTime giờ server lúc trả response; FE dùng để bù lệch đồng hồ máy người dùng khi đếm ngược
 */
public record TopUpOrderResponse(
        String orderCode,
        long amountVnd,
        int credits,
        PaymentOrderStatus status,
        PaymentProvider provider,
        String redirectUrl,
        Instant expiresAt,
        Instant serverTime) {}
