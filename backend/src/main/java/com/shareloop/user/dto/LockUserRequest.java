package com.shareloop.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu khoá hoặc mở khoá tài khoản người dùng")
public record LockUserRequest(
        @NotBlank(message = "Lý do không được để trống.")
        @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự.")
        @Schema(description = "Lý do thao tác", example = "Nhiều báo cáo lừa đảo đã được xác minh")
        String reason) {}
