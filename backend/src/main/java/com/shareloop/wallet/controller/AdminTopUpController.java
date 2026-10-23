package com.shareloop.wallet.controller;

import com.shareloop.common.api.PageResponse;
import com.shareloop.common.security.CurrentUserId;
import com.shareloop.wallet.dto.RejectTopUpRequest;
import com.shareloop.wallet.dto.ReviewTopUpRequest;
import com.shareloop.wallet.dto.TopUpOrderDetailResponse;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.service.ManualTopUpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/finance/topup-orders")
@Validated
public class AdminTopUpController {
    private final ManualTopUpService service;

    public AdminTopUpController(ManualTopUpService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TopUpOrderDetailResponse> list(
            // Bỏ trống status nghĩa là lấy mọi trạng thái; submittedOnly mặc định false nên thấy cả đơn chưa gửi.
            @RequestParam(required = false) PaymentOrderStatus status,
            @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(status, submittedOnly, page, size);
    }

    @PostMapping("/{orderCode}/approve")
    public TopUpOrderDetailResponse approve(
            @CurrentUserId Long adminId,
            @PathVariable String orderCode,
            @Valid @RequestBody ReviewTopUpRequest request) {
        return service.approve(adminId, orderCode, request.note());
    }

    @PostMapping("/{orderCode}/reject")
    public TopUpOrderDetailResponse reject(
            @CurrentUserId Long adminId,
            @PathVariable String orderCode,
            @Valid @RequestBody RejectTopUpRequest request) {
        return service.reject(adminId, orderCode, request.reason());
    }
}
