package com.shareloop.auth.dto;

/** Kết quả đăng nhập: JWT cùng thông tin người dùng. expiresIn tính bằng giây. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, AuthUserResponse user) {}
