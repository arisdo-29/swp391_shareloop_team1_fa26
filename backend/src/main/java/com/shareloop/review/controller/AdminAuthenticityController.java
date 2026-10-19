package com.shareloop.review.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.review.dto.AuthenticityDecisionRequest;
import com.shareloop.review.service.AuthenticityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "admin-authenticity-controller", description = "Admin đối chiếu chứng từ và quản lý nhãn chính hãng")
@Validated
@RestController
@RequestMapping("/api/v1/admin/authenticity")
public class AdminAuthenticityController {

    private final AuthenticityService authenticityService;

    public AdminAuthenticityController(AuthenticityService authenticityService) {
        this.authenticityService = authenticityService;
    }

    @Operation(
            summary = "Cấp nhãn chính hãng",
            description =
                    "Cấp nhãn chính hãng khi đối chiếu chứng từ đạt. Chuyển auth_status từ PENDING sang VERIFIED.")
    @ApiResponse(responseCode = "204", description = "Cấp nhãn thành công")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy bài đăng")
    @ApiResponse(responseCode = "409", description = "Bài đăng không ở trạng thái chờ xét duyệt chứng từ (PENDING)")
    @PostMapping("/{id}/grant")
    public ResponseEntity<Void> grant(
            @Parameter(description = "ID bài đăng") @PathVariable("id") long id,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        authenticityService.grant(id, adminId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Từ chối nhãn chính hãng",
            description =
                    "Từ chối cấp nhãn chính hãng kèm lý do bắt buộc. Chuyển auth_status từ PENDING sang REJECTED.")
    @ApiResponse(responseCode = "204", description = "Từ chối nhãn thành công")
    @ApiResponse(responseCode = "400", description = "Thiếu hoặc sai lý do từ chối")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy bài đăng")
    @ApiResponse(responseCode = "409", description = "Bài đăng không ở trạng thái chờ xét duyệt chứng từ (PENDING)")
    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(
            @Parameter(description = "ID bài đăng") @PathVariable("id") long id,
            @Valid @RequestBody AuthenticityDecisionRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        authenticityService.reject(id, request.reason(), adminId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Thu hồi nhãn chính hãng",
            description =
                    "Thu hồi nhãn khi phát hiện chứng từ giả. Chỉ áp dụng cho bài đang có nhãn VERIFIED. Chuyển sang REVOKED.")
    @ApiResponse(responseCode = "204", description = "Thu hồi nhãn thành công")
    @ApiResponse(responseCode = "400", description = "Thiếu hoặc sai lý do thu hồi")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy bài đăng")
    @ApiResponse(responseCode = "409", description = "Bài đăng chưa được cấp nhãn chính hãng (không phải VERIFIED)")
    @PostMapping("/{id}/revoke")
    public ResponseEntity<Void> revoke(
            @Parameter(description = "ID bài đăng") @PathVariable("id") long id,
            @Valid @RequestBody AuthenticityDecisionRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        authenticityService.revoke(id, request.reason(), adminId);
        return ResponseEntity.noContent().build();
    }
}
