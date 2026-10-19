package com.shareloop.listingfee.controller;

import com.shareloop.listingfee.dto.ListingFeeConfigResponse;
import com.shareloop.listingfee.service.ListingFeeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller mỏng: chỉ nhận request, gọi service, trả DTO thẳng (không bọc). Lỗi để GlobalExceptionHandler xử lý. */
@Tag(name = "ListingFee", description = "Phí đăng, phí sửa, gia hạn và đẩy bài")
@RestController
@RequestMapping("/api/v1/listing-fees")
public class ListingFeeController {

    private final ListingFeeQueryService listingFeeQueryService;

    public ListingFeeController(ListingFeeQueryService listingFeeQueryService) {
        this.listingFeeQueryService = listingFeeQueryService;
    }

    // Đường dẫn không nằm trong PUBLIC_GET của SecurityConfig nên mặc định bắt buộc đăng nhập.
    @Operation(
            summary = "Xem bảng phí đăng, sửa, gia hạn, đẩy bài hiện hành",
            description = "Mọi con số đọc từ cấu hình hệ thống; Admin đổi cấu hình thì lần gọi sau thấy số mới."
                    + " Bài GIVE luôn đăng miễn phí (postFeeGive = 0).")
    @ApiResponse(responseCode = "200", description = "Thành công")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @GetMapping
    public ListingFeeConfigResponse getFeeConfig() {
        return listingFeeQueryService.getFeeConfig();
    }
}
