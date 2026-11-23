package com.shareloop.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** G1 bổ sung: đơn chờ đối soát hiện câu cố định cho người dùng, không hiện ghi chú nội bộ. */
class PaymentFailureReasonTest {

    @Test
    void failureReason_whenPaidAfterClosedAndNotReviewed_returnsFixedSentence() {
        // given
        PaymentOrder order = new PaymentOrder(1L, "code-1", 50_000, 50, 1000, PaymentProvider.VNPAY);
        order.markExpired();
        order.flagPaidAfterClosed("Ghi chú nội bộ cho Admin", Instant.now(), "TX1");

        // when
        String reason = PaymentService.failureReason(order);

        // then
        assertThat(reason)
                .isEqualTo(
                        "VNPay đã báo thu tiền sau khi đơn hết hạn. Admin sẽ kiểm tra và cộng Credit hoặc hoàn tiền cho bạn.");
    }

    @Test
    void failureReason_whenExpiredWithoutPayment_returnsNull() {
        // given
        PaymentOrder order = new PaymentOrder(1L, "code-2", 50_000, 50, 1000, PaymentProvider.VNPAY);
        order.markExpired();

        // when, then
        assertThat(PaymentService.failureReason(order)).isNull();
    }
}
