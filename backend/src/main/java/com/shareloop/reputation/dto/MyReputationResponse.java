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

        @Schema(description = "Lịch sử biến động điểm uy tín, sắp xếp mới nhất lên trước")
        Page<PointHistoryDto> history,

        @Schema(description = "Hạng kế tiếp, null nếu đã ở hạng cao nhất", example = "DIAMOND")
        Rank nextRank,

        @Schema(description = "Số điểm còn thiếu để lên hạng kế tiếp, null nếu đã ở hạng cao nhất", example = "80")
        Integer pointsToNextRank,

        @Schema(description = "Điểm tối thiểu của hạng hiện tại", example = "200")
        int currentRankMinPoints,

        @Schema(description = "Điểm tối thiểu của hạng kế tiếp, null nếu đã ở hạng cao nhất", example = "400")
        Integer nextRankMinPoints) {

    /** Dùng khi không cần thông tin hạng kế tiếp (giữ tương thích các chỗ tạo cũ). */
    public MyReputationResponse(int totalPoints, Rank rank, int trustStars, Page<PointHistoryDto> history) {
        this(totalPoints, rank, trustStars, history, null, null, 0, null);
    }
}
