package com.shareloop.wallet.dto;

import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import java.time.Instant;

/**
 * Chi tiết một đơn nạp.
 *
 * @param userName họ tên chủ đơn; chỉ có trong API của Admin, API của người dùng để null
 * @param userEmail email chủ đơn; chỉ có trong API của Admin, API của người dùng để null
 * @param reviewedByName họ tên Admin đã duyệt hoặc từ chối; null khi đơn chưa được xử lý
 */
public record TopUpOrderDetailResponse(
        String orderCode,
        long userId,
        long amountVnd,
        int credits,
        int vndPerCredit,
        PaymentOrderStatus status,
        PaymentProvider provider,
        Instant createdAt,
        Instant expiresAt,
        Instant submittedAt,
        Long reviewedBy,
        Instant reviewedAt,
        String reviewNote,
        String userName,
        String userEmail,
        String reviewedByName) {}
