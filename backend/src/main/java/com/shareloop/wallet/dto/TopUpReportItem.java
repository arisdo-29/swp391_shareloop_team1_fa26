package com.shareloop.wallet.dto;

import com.shareloop.wallet.entity.PaymentProvider;
import java.time.Instant;

/**
 * Chi tiết đơn nạp tiền thành công phục vụ xuất báo cáo tài chính (L02).
 *
 * @param paidAt thời điểm thanh toán
 * @param orderCode mã đơn nạp
 * @param userId mã người dùng
 * @param fullName họ và tên
 * @param email địa chỉ email
 * @param amountVnd số tiền nạp (VND)
 * @param credits số Credit nhận được
 * @param provider cổng thanh toán (VNPAY, MOCK, MANUAL)
 */
public record TopUpReportItem(
        Instant paidAt,
        String orderCode,
        Long userId,
        String fullName,
        String email,
        long amountVnd,
        int credits,
        PaymentProvider provider) {}
