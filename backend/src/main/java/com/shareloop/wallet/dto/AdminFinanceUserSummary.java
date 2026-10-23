package com.shareloop.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Một người dùng cùng số liệu Credit cơ bản, dùng cho màn hình tra cứu tài chính của Admin (FR-112).
 *
 * @param userId id người dùng
 * @param fullName họ tên
 * @param email email
 * @param phone số điện thoại
 * @param availableCredit Credit dùng được = tổng Credit trừ phần đang giữ
 * @param heldCredit Credit đang bị giữ chờ duyệt bài
 * @param totalCredit tổng Credit đang có (khả dụng cộng đang giữ)
 * @param totalToppedUpCredit tổng Credit đã nạp thành công từ trước đến nay
 */
@Schema(description = "Người dùng kèm số liệu Credit cơ bản")
public record AdminFinanceUserSummary(
        Long userId,
        String fullName,
        String email,
        String phone,
        long availableCredit,
        long heldCredit,
        long totalCredit,
        long totalToppedUpCredit) {}
