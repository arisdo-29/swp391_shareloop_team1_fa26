package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Yêu cầu bật/tắt trạng thái câu hỏi checklist")
public record AdminChecklistActiveRequest(
        @NotNull(message = "active không được để trống.")
        @Schema(description = "Trạng thái hoạt động (true: kích hoạt, false: ẩn)", example = "false")
        Boolean active) {}
