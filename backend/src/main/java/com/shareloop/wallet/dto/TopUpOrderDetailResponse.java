package com.shareloop.wallet.dto;

import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import java.time.Instant;

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
        String reviewNote) {}
