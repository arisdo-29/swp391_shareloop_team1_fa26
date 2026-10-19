package com.shareloop.wallet.repository;

import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;

/**
 * Ảnh chụp chỉ-đọc của một đơn nạp, dùng để tìm đơn theo order_code và báo lỗi sớm trước khi khoá. Interface
 * projection: Spring Data tự tạo đối tượng từ kết quả query, không phải entity nên không vào persistence context
 * (lần khoá đơn sau đó sẽ đọc dữ liệu mới từ DB).
 */
public interface PaymentOrderSnapshot {

    PaymentProvider getProvider();

    Long getId();

    Long getUserId();

    PaymentOrderStatus getStatus();

    int getCredits();
}
