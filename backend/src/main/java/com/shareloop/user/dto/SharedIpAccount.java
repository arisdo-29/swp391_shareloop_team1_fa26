package com.shareloop.user.dto;

import com.shareloop.user.entity.UserStatus;
import java.time.Instant;

/**
 * Thông tin một tài khoản trong nhóm cảnh báo cùng địa chỉ IP (AD-04).
 *
 * @param id id người dùng
 * @param fullName họ tên
 * @param email email tài khoản
 * @param registeredAt ngày đăng ký (created_at)
 * @param trustStars số sao uy tín hiện tại
 * @param status trạng thái tài khoản (ACTIVE, BANNED)
 * @param locked true nếu tài khoản đang bị khoá (is_active = false)
 */
public record SharedIpAccount(
        long id,
        String fullName,
        String email,
        Instant registeredAt,
        int trustStars,
        UserStatus status,
        boolean locked) {}
