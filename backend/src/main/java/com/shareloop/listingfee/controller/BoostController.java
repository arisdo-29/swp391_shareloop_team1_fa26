package com.shareloop.listingfee.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.listingfee.dto.BoostResponse;
import com.shareloop.listingfee.service.BoostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller mỏng: chỉ nhận request, gọi service, trả DTO thẳng (không bọc). Lỗi để GlobalExceptionHandler xử lý. */
@Tag(name = "ListingFee", description = "Phí đăng, phí sửa, gia hạn và đẩy bài")
@RestController
@RequestMapping("/api/v1/items")
public class BoostController {

    private final BoostService boostService;

    public BoostController(BoostService boostService) {
        this.boostService = boostService;
    }

    // POST không nằm trong PUBLIC_GET nên bắt buộc có token; id người dùng lấy từ token, không nhận từ client.
    @Operation(summary = "Đẩy bài của tôi thêm một gói (trừ phí đẩy bài ngay)")
    @PostMapping("/{id}/boost")
    public BoostResponse boost(@PathVariable long id, @Parameter(hidden = true) @CurrentUserId Long currentUserId) {
        return boostService.boost(id, currentUserId);
    }
}
