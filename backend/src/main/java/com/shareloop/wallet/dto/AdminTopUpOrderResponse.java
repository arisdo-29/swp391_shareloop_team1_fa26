package com.shareloop.wallet.dto;

import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import java.time.Instant;

/**
 * Một đơn nạp trong trang Giao dịch nạp tiền của Admin. Không có URL thanh toán.
 *
 * @param providerTxnId mã giao dịch phía VNPay; null khi chưa có callback
 * @param paidAt lúc VNPay báo đã thu tiền; có cả ở đơn đã đóng mà tiền vẫn về (khi đó cần đối soát)
 * @param needsReconcile true khi VNPay đã thu tiền nhưng đơn đã đóng và Admin chưa xử lý
 * @param reviewedByName họ tên Admin đã xử lý đối soát; null khi chưa xử lý
 */
public record AdminTopUpOrderResponse(
        String orderCode,
        long userId,
        String userName,
        String userEmail,
        long amountVnd,
        int credits,
        PaymentProvider provider,
        PaymentOrderStatus status,
        String providerTxnId,
        Instant createdAt,
        Instant paidAt,
        boolean needsReconcile,
        Instant reviewedAt,
        String reviewedByName,
        String reviewNote) {}
