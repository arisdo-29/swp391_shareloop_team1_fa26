package com.shareloop.admin.dto;

/**
 * Một bên của giao dịch trong trang chi tiết của Admin.
 *
 * @param trustStars sao uy tín hiện tại, null khi không tra được
 * @param rank hạng hiện tại (BRONZE, SILVER...), null khi không tra được
 * @param locked true khi tài khoản đang bị khoá
 */
public record AdminRequestParty(
        Long userId, String name, String email, Integer trustStars, String rank, boolean locked) {}
