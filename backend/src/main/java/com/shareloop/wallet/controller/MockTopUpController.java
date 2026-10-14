package com.shareloop.wallet.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.wallet.dto.TopUpConfirmResponse;
import com.shareloop.wallet.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Xác nhận thanh toán giả lập: người dùng tự bấm "đã thanh toán" và được cộng Credit, không có tiền thật. Vì vậy
 * controller chỉ được tạo khi bật {@code app.payment.mock.enabled=true} (dev, staging); tắt thì endpoint không
 * tồn tại và trả 404.
 */
// @ConditionalOnProperty: Spring chỉ tạo bean này khi property có đúng giá trị; thiếu property coi như tắt.
@ConditionalOnProperty(name = "app.payment.mock.enabled", havingValue = "true")
@Tag(name = "Top-up", description = "Nạp Credit")
@RestController
@RequestMapping("/api/v1/wallet/topup-orders")
public class MockTopUpController {

    private final PaymentService paymentService;

    public MockTopUpController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Xác nhận thanh toán giả lập cho đơn nạp (chỉ dev, staging)")
    @PostMapping("/{orderCode}/mock-confirm")
    public TopUpConfirmResponse confirmMockPayment(
            @Parameter(hidden = true) @CurrentUserId Long currentUserId, @PathVariable String orderCode) {
        return paymentService.confirmMockPayment(currentUserId, orderCode);
    }
}
