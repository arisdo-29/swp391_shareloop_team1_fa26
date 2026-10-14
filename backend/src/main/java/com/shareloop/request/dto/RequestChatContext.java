package com.shareloop.request.dto;

import com.shareloop.request.entity.RequestStatus;

/**
 * Ngữ cảnh quyền truy cập và trạng thái của yêu cầu giao dịch dùng cho module chat.
 *
 * @param requestId  id của yêu cầu giao dịch
 * @param status     trạng thái hiện tại của yêu cầu
 * @param receiverId id người gửi yêu cầu (người nhận đồ)
 * @param donorId    id chủ bài đăng (người tặng / trao đổi đồ)
 */
public record RequestChatContext(Long requestId, RequestStatus status, Long receiverId, Long donorId) {}
