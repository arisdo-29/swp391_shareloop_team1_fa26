package com.shareloop.reputation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu cấp sao ân hạn cho người dùng")
public record GraceStarRequest(
        @NotBlank(message = "Lý do không được để trống.")
        @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự.")
        @Schema(description = "Lý do cấp sao ân hạn", example = "Đã giải trình và cam kết không tái phạm")
        String reason) {}
