package com.shareloop.review.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Answers map by position to the active checklist returned by the pending-review endpoint. */
public record ApproveReviewRequest(
        @NotEmpty(message = "Cần trả lời checklist trước khi duyệt bài.")
        List<@NotNull Boolean> answers) {}
