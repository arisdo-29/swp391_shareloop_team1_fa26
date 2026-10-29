package com.shareloop.wallet.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.dto.WalletTransactionPage;
import com.shareloop.wallet.service.CreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller mỏng: chỉ nhận request, gọi service, trả DTO thẳng (không bọc). Lỗi để GlobalExceptionHandler xử lý. */
// @Tag: Swagger UI gom các endpoint cùng tag vào một nhóm riêng tên "Wallet".
@Tag(name = "Wallet", description = "Ví Credit của người dùng")
// @Validated: bật kiểm @Min / @Max trên tham số query; sai thì trả 400 VALIDATION_FAILED.
@Validated
@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final CreditService creditService;

    public WalletController(CreditService creditService) {
        this.creditService = creditService;
    }

    // Không nằm trong PUBLIC_GET của SecurityConfig nên bắt buộc có token; id lấy từ token, không nhận từ client.
    @Operation(summary = "Xem ví Credit của tôi")
    @GetMapping
    public WalletResponse getMyWallet(@Parameter(hidden = true) @CurrentUserId Long currentUserId) {
        return creditService.getWallet(currentUserId);
    }

    // from, to dạng ISO-8601 UTC (ví dụ 2026-10-01T00:00:00Z); Spring tự đổi chuỗi sang Instant.
    @Operation(summary = "Lịch sử ví của tôi, mới nhất trước")
    @GetMapping("/transactions")
    public WalletTransactionPage listMyTransactions(
            @Parameter(hidden = true) @CurrentUserId Long currentUserId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return creditService.getMyTransactions(currentUserId, from, to, page, size);
    }
}
