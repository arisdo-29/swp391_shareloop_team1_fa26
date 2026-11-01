package com.shareloop.listingfee.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.listingfee.dto.EditQuote;
import com.shareloop.listingfee.service.PostFeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller mỏng: chỉ nhận request, gọi service, trả DTO thẳng (không bọc). Lỗi để GlobalExceptionHandler xử lý. */
@Tag(name = "ListingFee", description = "Phí đăng và phí sửa bài")
@RestController
@RequestMapping("/api/v1/items")
public class EditQuoteController {

    private final PostFeeService postFeeService;

    public EditQuoteController(PostFeeService postFeeService) {
        this.postFeeService = postFeeService;
    }

    // PUBLIC_GET chỉ mở "/api/v1/items/*" (một đoạn sau /items), đường dẫn này có hai đoạn nên bắt buộc có token;
    // id người dùng lấy từ token, không nhận từ client.
    @Operation(summary = "Báo giá lượt sửa bài của tôi (miễn phí hay giữ bao nhiêu Credit)")
    @GetMapping("/{id}/edit-quote")
    public EditQuote getEditQuote(@PathVariable long id, @Parameter(hidden = true) @CurrentUserId Long currentUserId) {
        return postFeeService.quoteEdit(id, currentUserId);
    }
}
