package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Yêu cầu bật hoặc tắt trạng thái hoạt động của từ khóa")
public record AdminLexiconActiveRequest(
        @NotNull @Schema(description = "Trạng thái hoạt động (true: kích hoạt, false: ẩn)", example = "false")
        Boolean active) {}
