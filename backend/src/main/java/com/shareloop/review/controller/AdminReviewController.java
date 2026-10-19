package com.shareloop.review.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.review.dto.ApproveReviewRequest;
import com.shareloop.review.dto.RejectReviewRequest;
import com.shareloop.review.dto.ReviewQueueItem;
import com.shareloop.review.dto.ReviewQueueResponse;
import com.shareloop.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "admin-review-controller", description = "Admin duyệt bài đăng đang chờ kiểm duyệt")
@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(
            summary = "Danh sách hàng chờ duyệt (tương thích cũ)",
            description = "Lấy danh sách các bài đăng đang chờ duyệt (PENDING_REVIEW), không kèm checklist.")
    @ApiResponse(responseCode = "200", description = "Trả về danh sách bài đăng chờ duyệt")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @GetMapping("/items/pending")
    public List<ReviewQueueItem> listPending(
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang (1-100)")
                    @RequestParam(defaultValue = "20")
                    @Min(1)
                    @Max(100)
                    int size) {
        return reviewService.listPending(page, size);
    }

    @Operation(
            summary = "Danh sách hàng chờ duyệt",
            description =
                    "Sắp xếp: bài có cờ REVIEW trước, người đăng có trust_stars thấp trước, tài khoản mới hơn trước, bài chờ lâu hơn trước. Kèm checklist kiểm duyệt hiện hành.")
    @ApiResponse(responseCode = "200", description = "Trả về hàng chờ đã sắp xếp kèm checklist")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @GetMapping("/reviews/pending")
    public ReviewQueueResponse listPendingReviews(
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang (1-100)")
                    @RequestParam(defaultValue = "20")
                    @Min(1)
                    @Max(100)
                    int size) {
        return reviewService.listPendingWithChecklist(page, size);
    }

    @Operation(
            summary = "Duyệt bài",
            description =
                    "Duyệt bài đăng đang chờ kiểm duyệt. Gửi đủ đáp án theo id của điều kiện checklist, không trùng id và tất cả passed = true. Hệ thống trừ phí đang giữ và chuyển trạng thái bài sang APPROVED.")
    @ApiResponse(responseCode = "204", description = "Duyệt thành công")
    @ApiResponse(responseCode = "400", description = "Checklist chưa hoàn thành hoặc có câu trả lời không đạt")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy item")
    @ApiResponse(
            responseCode = "409",
            description = "Checklist lỗi thời/chưa cấu hình, item đã được xử lý trước đó hoặc không có phí đang giữ")
    @PostMapping("/reviews/{itemId}/approve")
    public ResponseEntity<Void> approve(
            @Parameter(description = "ID của item trong hàng chờ") @PathVariable long itemId,
            @Valid @RequestBody ApproveReviewRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        reviewService.approve(itemId, request.answers(), adminId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Danh sách mẫu lý do từ chối",
            description = "Lấy các mẫu REJECT_REASON đang kích hoạt để Admin chọn khi từ chối.")
    @ApiResponse(responseCode = "200", description = "Trả về danh sách lý do mẫu")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @GetMapping("/reviews/reject-reasons")
    public List<String> listRejectReasons() {
        return reviewService.listRejectReasons();
    }

    @Operation(
            summary = "Từ chối bài",
            description =
                    "Từ chối bài đăng kèm lý do chọn từ danh sách reject-reasons hoặc tự nhập, hoàn trả phí đang giữ và gửi thông báo kèm lý do cho người đăng.")
    @ApiResponse(responseCode = "204", description = "Từ chối thành công")
    @ApiResponse(responseCode = "400", description = "Thiếu hoặc sai lý do từ chối")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy item")
    @ApiResponse(responseCode = "409", description = "Item đã được xử lý trước đó")
    @PostMapping("/reviews/{itemId}/reject")
    public ResponseEntity<Void> reject(
            @Parameter(description = "ID của item trong hàng chờ") @PathVariable long itemId,
            @Valid @RequestBody RejectReviewRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        reviewService.reject(itemId, request.reason(), adminId);
        return ResponseEntity.noContent().build();
    }
}
