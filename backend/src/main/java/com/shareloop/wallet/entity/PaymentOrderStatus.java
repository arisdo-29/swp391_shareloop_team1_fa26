package com.shareloop.wallet.entity;

/**
 * Trạng thái của {@code payment_orders.status}. Đúng các giá trị của CHECK trong DB (V1__init.sql, thêm CANCELLED ở
 * V202611211100).
 */
public enum PaymentOrderStatus {
    /** Vừa tạo, chờ cổng thanh toán xác nhận. */
    PENDING,
    /** Đã thanh toán và đã cộng Credit; đơn SUCCESS chính là sổ tiền mặt thực thu. */
    SUCCESS,
    /** Cổng báo thanh toán thất bại. */
    FAILED,
    /** Quá thời hạn chờ (payment_order_expire_minutes) mà không có phản hồi. */
    EXPIRED,
    /** Đã hủy: người dùng tự hủy trên trang Ví, hủy để tạo đơn mới, hoặc khách hủy tại VNPay (mã 24). */
    CANCELLED
}
