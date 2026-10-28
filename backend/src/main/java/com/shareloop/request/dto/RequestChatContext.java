package com.shareloop.request.dto;

import com.shareloop.request.entity.RequestStatus;

/**
 * Ngữ cảnh quyền truy cập và trạng thái của yêu cầu giao dịch dùng cho module chat.
 *
 * @param requestId     id của yêu cầu giao dịch
 * @param status        trạng thái hiện tại của yêu cầu
 * @param receiverId    id người gửi yêu cầu (người nhận đồ)
 * @param donorId       id chủ bài đăng (người tặng / trao đổi đồ)
 * @param itemId        id bài đăng chính của yêu cầu
 * @param offeredItemId id món đề nghị trao đổi nếu là bài SWAP (có thể null)
 * @param type          GIVE hoặc SWAP; null khi nơi dựng ngữ cảnh không biết loại giao dịch
 */
public record RequestChatContext(
        Long requestId,
        RequestStatus status,
        Long receiverId,
        Long donorId,
        Long itemId,
        Long offeredItemId,
        String type) {

    public RequestChatContext(
            Long requestId, RequestStatus status, Long receiverId, Long donorId, Long itemId, Long offeredItemId) {
        this(requestId, status, receiverId, donorId, itemId, offeredItemId, null);
    }

    public RequestChatContext(Long requestId, RequestStatus status, Long receiverId, Long donorId) {
        this(requestId, status, receiverId, donorId, null, null, null);
    }
}
