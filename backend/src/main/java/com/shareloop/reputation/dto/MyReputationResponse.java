package com.shareloop.reputation.dto;

import com.shareloop.reputation.entity.Rank;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

@Schema(description = "Thông tin uy tín của chính người dùng đang đăng nhập")
public record MyReputationResponse(
        @Schema(description = "Tổng điểm uy tín hiện tại", example = "320")
        int totalPoints,

        @Schema(description = "Hạng tín nhiệm hiện tại", example = "GOLD")
        Rank rank,

        @Schema(description = "Số sao uy tín còn lại (0-5)", example = "4")
        int trustStars,

        @Schema(
                description = "Tỉ lệ đúng mô tả (0.0-1.0), null nếu chưa có giao dịch nào được đánh giá",
                example = "0.92")
        Double accuracyRate,

        @Schema(description = "Lịch sử biến động điểm uy tín, sắp xếp mới nhất lên trước")
        Page<PointHistoryDto> history) {}
