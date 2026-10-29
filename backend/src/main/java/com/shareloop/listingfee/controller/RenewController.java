package com.shareloop.listingfee.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.listingfee.dto.RenewQuote;
import com.shareloop.listingfee.dto.RenewResponse;
import com.shareloop.listingfee.service.RenewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller mỏng: chỉ nhận request, gọi service, trả DTO thẳng (không bọc). Lỗi để GlobalExceptionHandler xử lý. */
@Tag(name = "ListingFee", description = "Phí đăng, phí sửa và gia hạn bài")
@RestController
@RequestMapping("/api/v1/items")
public class RenewController {

    private final RenewService renewService;

    public RenewController(RenewService renewService) {
        this.renewService = renewService;
    }

    // GET hai đoạn sau /items không nằm trong PUBLIC_GET nên bắt buộc có token; id người dùng lấy từ token.
    @Operation(summary = "Báo giá một lần gia hạn bài của tôi (phí, hạn mới, có được gia hạn không)")
    @GetMapping("/{id}/renew-quote")
    public RenewQuote getRenewQuote(
            @PathVariable long id, @Parameter(hidden = true) @CurrentUserId Long currentUserId) {
        return renewService.quoteRenew(id, currentUserId);
    }

    // POST không nằm trong PUBLIC_GET nên bắt buộc có token; id người dùng lấy từ token, không nhận từ client.
    @Operation(summary = "Gia hạn bài của tôi (trừ phí gia hạn ngay, không duyệt lại)")
    @PostMapping("/{id}/renew")
    public RenewResponse renew(@PathVariable long id, @Parameter(hidden = true) @CurrentUserId Long currentUserId) {
        return renewService.renew(id, currentUserId);
    }
}
