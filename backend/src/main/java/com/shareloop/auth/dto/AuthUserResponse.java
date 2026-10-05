package com.shareloop.auth.dto;

/** Thông tin tối thiểu của người dùng, dùng trong AuthResponse và GET /api/v1/me. */
public record AuthUserResponse(long id, String email, String fullName, boolean isAdmin) {}
