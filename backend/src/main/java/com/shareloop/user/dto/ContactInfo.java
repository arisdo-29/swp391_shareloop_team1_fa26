package com.shareloop.user.dto;

/**
 * Thông tin liên lạc của một người dùng, dùng để trao cho đối tác khi hai bên chốt lịch.
 *
 * @param userId id người dùng
 * @param fullName họ tên hiển thị
 * @param email email (luôn được trao cùng)
 * @param phone số điện thoại
 */
public record ContactInfo(long userId, String fullName, String email, String phone) {}
