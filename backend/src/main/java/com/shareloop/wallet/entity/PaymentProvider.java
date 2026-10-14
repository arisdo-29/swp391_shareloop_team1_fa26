package com.shareloop.wallet.entity;

/** Cổng thanh toán của một đơn nạp ({@code payment_orders.provider}, ERD mục 7.6). */
public enum PaymentProvider {
    /** VNPay sandbox (chưa làm). */
    VNPAY,
    /** Cổng giả lập: không có tiền thật, dùng cho dev và demo. */
    MOCK
}
