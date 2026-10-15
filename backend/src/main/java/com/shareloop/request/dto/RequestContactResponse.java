package com.shareloop.request.dto;

/**
 * DTO phản hồi thông tin liên lạc của đối tác khi giao dịch đã chốt lịch (FR-82, BR-T11).
 *
 * @param userId id người dùng đối tác
 * @param fullName họ tên hiển thị
 * @param email email liên lạc
 * @param phone số điện thoại
 */
public record RequestContactResponse(long userId, String fullName, String email, String phone) {}
