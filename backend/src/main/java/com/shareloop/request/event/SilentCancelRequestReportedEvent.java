package com.shareloop.request.event;

/**
 * Phát ra khi đề nghị huỷ với lý do "bên kia không đến điểm hẹn" hoặc "chưa gửi hàng" hết hạn mà bên kia im lặng
 * (GN-06). Giao dịch đã huỷ, module report mở báo cáo không đến hẹn nhắm vào bên im lặng để Admin xem. Không tự trừ sao.
 *
 * @param issuePhrase câu mô tả vấn đề cho người bị báo, ví dụ "không đến điểm hẹn"
 */
public record SilentCancelRequestReportedEvent(
        long requestId, long reporterId, long reportedUserId, String issuePhrase, String note) {}
