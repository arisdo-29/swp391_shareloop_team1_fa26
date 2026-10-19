package com.shareloop.user.dto;

/** Thông tin rút gọn của một người dùng để module khác hiển thị tên (ví dụ trang nhật ký của Admin). */
public record UserBrief(Long id, String fullName, String email) {}
