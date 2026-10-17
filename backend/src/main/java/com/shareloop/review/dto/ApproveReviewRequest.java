package com.shareloop.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Answers map by position to the active checklist returned by the pending-review endpoint. */
@Schema(description = "Yêu cầu duyệt bài đăng kèm câu trả lời checklist")
public record ApproveReviewRequest(
        @NotEmpty(message = "Cần trả lời checklist trước khi duyệt bài.")
        @Schema(
                description =
                        "Danh sách câu trả lời boolean tương ứng với từng câu hỏi checklist (tất cả phải là true)",
                example = "[true, true, true]")
        List<@NotNull Boolean> answers) {}
