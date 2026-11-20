package com.shareloop.wallet.controller;

import com.shareloop.common.api.PageResponse;
import com.shareloop.common.security.CurrentUserId;
import com.shareloop.wallet.dto.CreateTopUpOrderRequest;
import com.shareloop.wallet.dto.TopUpOrderDetailResponse;
import com.shareloop.wallet.dto.TopUpOrderResponse;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tạo và xem đơn nạp Credit (docs/api/payment.md). Controller mỏng: nhận request, gọi service, trả DTO. */
@Tag(name = "Top-up", description = "Nạp Credit")
@RestController
@RequestMapping("/api/v1/wallet/topup-orders")
public class TopUpOrderController {

    private final PaymentService paymentService;

    public TopUpOrderController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // @Valid: kiểm các ràng buộc trong CreateTopUpOrderRequest trước khi vào hàm; sai thì trả 400 VALIDATION_FAILED.
    @Operation(summary = "Tạo đơn nạp Credit")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TopUpOrderResponse createTopUpOrder(
            @Parameter(hidden = true) @CurrentUserId Long currentUserId,
            @Valid @RequestBody CreateTopUpOrderRequest request) {
        return paymentService.createTopUpOrder(
                currentUserId, request.amountVnd(), Boolean.TRUE.equals(request.replacePending()));
    }

    // Danh sách đơn nạp của chính mình: status=PENDING cho mục "Đơn đang chờ thanh toán", không có status là lịch sử.
    @Operation(
            summary = "Danh sách đơn nạp Credit của chính mình",
            description = "Mới nhất trước, phân trang. status=PENDING chỉ trả đơn còn trong hạn thanh toán.")
    @GetMapping
    public PageResponse<TopUpOrderDetailResponse> listOrders(
            @Parameter(hidden = true) @CurrentUserId Long currentUserId,
            @RequestParam(required = false) PaymentOrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return paymentService.listOwnOrders(currentUserId, status, page, size);
    }

    // B29: endpoint này bị gỡ cùng luồng nạp thủ công (#435) trong khi trang Ví vẫn gọi khi quay về từ VNPAY.
    @Operation(
            summary = "Xem một đơn nạp Credit của chính mình",
            description = "Trả trạng thái và hạn thanh toán expiresAt.")
    @GetMapping("/{orderCode}")
    public TopUpOrderDetailResponse getOrder(
            @Parameter(hidden = true) @CurrentUserId Long currentUserId, @PathVariable String orderCode) {
        return paymentService.getOwnOrder(currentUserId, orderCode);
    }

    @Operation(
            summary = "Hủy đơn nạp Credit đang chờ thanh toán",
            description = "Đơn PENDING chuyển FAILED ngay. Gọi lại không đổi gì; luôn trả trạng thái hiện tại của đơn.")
    @PostMapping("/{orderCode}/cancel")
    public TopUpOrderDetailResponse cancelOrder(
            @Parameter(hidden = true) @CurrentUserId Long currentUserId, @PathVariable String orderCode) {
        return paymentService.cancelOwnOrder(currentUserId, orderCode);
    }
}
