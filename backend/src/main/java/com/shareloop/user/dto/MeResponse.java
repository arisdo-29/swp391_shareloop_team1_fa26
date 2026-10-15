package com.shareloop.user.dto;

/**
 * Hồ sơ của người đang đăng nhập, trả từ GET và PUT /api/v1/me. Có đủ các trường của AuthUserResponse nên FE đang
 * đọc id, email, fullName, isAdmin không bị ảnh hưởng.
 *
 * @param areaId khu vực mặc định, null khi chưa chọn
 * @param avatarMediaId id ảnh đại diện, null khi chưa có
 * @param avatarUrl đường dẫn đọc ảnh (GET /api/v1/media/{id}), null khi chưa có ảnh
 */
public record MeResponse(
        long id,
        String email,
        String fullName,
        String phone,
        boolean isAdmin,
        Long areaId,
        Long avatarMediaId,
        String avatarUrl) {}
