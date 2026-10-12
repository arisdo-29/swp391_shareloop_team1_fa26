package com.shareloop.wallet.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.service.CreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller mỏng: chỉ nhận request, gọi service, trả DTO thẳng (không bọc). Lỗi để GlobalExceptionHandler xử lý. */
// @Tag: Swagger UI gom các endpoint cùng tag vào một nhóm riêng tên "Wallet".
@Tag(name = "Wallet", description = "Ví Credit của người dùng")
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
    public WalletResponse getMyWallet(@CurrentUserId Long currentUserId) {
        return creditService.getWallet(currentUserId);
    }
}
