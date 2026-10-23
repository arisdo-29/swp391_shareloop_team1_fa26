package com.shareloop.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Một dòng trong danh sách tài khoản của Admin")
public record AdminUserSummary(
        @Schema(description = "ID tài khoản", example = "9") Long id,

        @Schema(description = "Họ tên", example = "Nguyễn Văn A")
        String fullName,

        @Schema(description = "Email đăng nhập", example = "a@example.com")
        String email,

        @Schema(description = "Số điện thoại", example = "0901234567")
        String phone,

        @Schema(description = "Vai trò: ADMIN hoặc USER", example = "USER")
        AdminAccountRole role,

        @Schema(
                description = "Trạng thái hiển thị: ACTIVE, LOCKED, PENDING_VERIFICATION hoặc BANNED",
                example = "ACTIVE")
        AdminAccountState status,

        @Schema(description = "true khi tài khoản đang bị khoá (is_active = false)", example = "false")
        boolean locked,

        @Schema(description = "Thời điểm tạo tài khoản", example = "2026-10-14T10:00:00Z")
        Instant createdAt) {}
