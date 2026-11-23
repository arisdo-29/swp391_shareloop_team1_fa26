package com.shareloop.admin.dto;

import java.time.Instant;

/**
 * Đề nghị huỷ của giao dịch nhìn từ Admin (GN-06): đang chờ hay đã xử lý, ai đề nghị, lý do.
 *
 * @param state PENDING (đang chờ phản hồi), ACCEPTED (giao dịch đã huỷ vì bên kia đồng ý hoặc im lặng) hoặc REJECTED
 *     (bên kia không đồng ý nên sang sự cố giao nhận)
 * @param requestedByRole "Chủ bài" hoặc "Người nhận"
 * @param label nhãn tiếng Việt của mã lý do
 * @param reason chữ người dùng nhập thêm, null khi không có
 * @param expiresAt hạn phản hồi, chỉ có khi đang chờ
 */
public record AdminCancelRequest(
        String state, String requestedByRole, Instant requestedAt, String label, String reason, Instant expiresAt) {}
