package com.shareloop.reputation.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.reputation.dto.MyReputationResponse;
import com.shareloop.reputation.service.ReputationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "me-reputation-controller", description = "Xem thông tin uy tín của chính người dùng đang đăng nhập")
@RestController
@RequestMapping("/api/v1/me/reputation")
public class MeReputationController {

    private final ReputationService reputationService;

    public MeReputationController(ReputationService reputationService) {
        this.reputationService = reputationService;
    }

    @Operation(
            summary = "Xem điểm, hạng và lịch sử uy tín của mình",
            description =
                    "Cần đăng nhập. Trả về tổng điểm, hạng, TrustStars, tỉ lệ đúng mô tả và lịch sử biến động điểm (mới nhất trước, có phân trang).")
    @ApiResponse(responseCode = "200", description = "Lấy thông tin uy tín thành công")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @GetMapping
    public MyReputationResponse getMyReputation(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "Chỉ số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang (1-50)") @RequestParam(defaultValue = "20") int size) {
        return reputationService.getMyReputation(userId, page, size);
    }
}

