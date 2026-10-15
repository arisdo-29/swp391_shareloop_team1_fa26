package com.shareloop.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body của PUT /api/v1/me. Gửi đủ cả ba trường mỗi lần: areaId hoặc avatarMediaId là null nghĩa là bỏ giá trị đó.
 * Không có email và số điện thoại: hai trường này không đổi ở endpoint này (FR-06, FR-07).
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String fullName,
        @Positive Long areaId,
        @Positive Long avatarMediaId) {}
