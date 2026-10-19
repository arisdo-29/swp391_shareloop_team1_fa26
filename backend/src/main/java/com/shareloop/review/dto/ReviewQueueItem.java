package com.shareloop.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Schema(description = "Thông tin bài đăng trong hàng chờ duyệt")
public record ReviewQueueItem(
        @Schema(description = "ID bài đăng trong hàng chờ", example = "42")
        Long id,

        @Schema(description = "ID người đăng bài", example = "9")
        Long donorId,

        @Schema(description = "Tiêu đề bài đăng", example = "Áo khoác gió nam")
        String title,

        @Schema(description = "Mức độ rủi ro do AI đánh giá (LOW, MEDIUM, HIGH)", example = "LOW")
        String aiRisk,

        @Schema(description = "Cờ kiểm duyệt (REVIEW nếu nghi vấn vi phạm)", example = "REVIEW")
        String moderationFlag,

        @Schema(
                description =
                        "Mã gốc lý do gắn cờ (LEXICON_REVIEW, INVALID_INPUT...), hoặc câu AI viết sẵn; null nếu không có cờ",
                example = "LEXICON_REVIEW")
        String flagReasonCode,

        @Schema(
                description = "Lý do bị gắn cờ bằng tiếng Việt để hiển thị cho Admin",
                example = "Có từ khoá cần xem xét: zalo")
        String flagReason,

        @Schema(description = "Từ khoá đã khớp khi kiểm duyệt tầng 1 (rỗng nếu không có)", example = "[\"zalo\"]")
        List<String> matchedTerms,

        @Schema(description = "Số sao uy tín của người đăng (1-5)", example = "3")
        short donorTrustStars,

        @Schema(description = "Thời điểm bài đăng được tạo", example = "2026-10-14T10:00:00Z")
        Instant createdAt) {

    public static final Comparator<ReviewQueueItem> QUEUE_ORDER = Comparator.comparing(
                    ReviewQueueItem::isFlagged, Comparator.reverseOrder())
            .thenComparingInt(ReviewQueueItem::donorTrustStars)
            .thenComparing(ReviewQueueItem::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ReviewQueueItem::id, Comparator.nullsLast(Comparator.naturalOrder()));

    public boolean isFlagged() {
        return "REVIEW".equalsIgnoreCase(moderationFlag);
    }

    public int riskScore() {
        return Math.max(0, 6 - donorTrustStars);
    }
}
