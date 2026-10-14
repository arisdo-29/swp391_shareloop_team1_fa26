package com.shareloop.wallet.entity;

/** Trạng thái của {@code payment_orders.status} (V1__init.sql, ERD mục 7.6). Đúng 4 giá trị của CHECK trong DB. */
public enum PaymentOrderStatus {
    /** Vừa tạo, chờ cổng thanh toán xác nhận. */
    PENDING,
    /** Đã thanh toán và đã cộng Credit; đơn SUCCESS chính là sổ tiền mặt thực thu. */
    SUCCESS,
    /** Cổng báo thanh toán thất bại. */
    FAILED,
    /** Quá thời hạn chờ (payment_order_expire_minutes) mà không có phản hồi. */
    EXPIRED
}
