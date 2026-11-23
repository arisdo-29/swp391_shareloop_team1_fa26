package com.shareloop.request.dto;

/**
 * Giao dịch đang mở của một bài (từ khi được chọn tới trước khi kết thúc), dùng cho module report khi bài bị báo cáo (F1).
 *
 * @param requestId id giao dịch
 * @param partnerUserId bên còn lại so với chủ của bài: người nhận nếu bài là bài chính, chủ bài chính nếu bài là món đổi
 */
public record OpenItemTransaction(Long requestId, Long partnerUserId) {}
