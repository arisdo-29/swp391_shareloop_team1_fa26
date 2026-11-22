package com.shareloop.admin.dto;

/** Điểm tích luỹ đã cộng cho một bên nhờ giao dịch hoàn tất. */
public record AdminRequestPoints(String userName, String role, int points) {}
