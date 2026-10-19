package com.shareloop.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Đáp án ghép theo id mục checklist (không theo vị trí), để Admin đổi thứ tự checklist không làm lệch đáp án. */
@Schema(description = "Yêu cầu duyệt bài đăng kèm đáp án checklist")
public record ApproveReviewRequest(
        @NotEmpty(message = "Cần trả lời checklist trước khi duyệt bài.")
        @Schema(
                description =
                        "Đáp án cho từng mục checklist đang dùng, ghép theo id. Phải đủ mọi mục, không trùng id và tất cả passed = true",
                example = "[{\"id\": 11, \"passed\": true}, {\"id\": 12, \"passed\": true}]")
        List<@Valid @NotNull ChecklistAnswer> answers) {}
