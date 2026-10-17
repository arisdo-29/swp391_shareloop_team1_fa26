package com.shareloop.chat.dto;

import jakarta.validation.constraints.Size;

/**
 * Yêu cầu gửi tin nhắn văn bản, ảnh hoặc thẻ bài đăng vào cuộc trò chuyện (FR-68, FR-70, FR-75).
 *
 * @param content   nội dung tin nhắn gửi đi (tuỳ chọn nếu gửi ảnh hoặc thẻ bài)
 * @param mediaId   id của ảnh đã tải lên từ MediaService (tuỳ chọn)
 * @param refItemId id của bài đăng muốn chia sẻ thẻ vào chat (tuỳ chọn, phải là bài APPROVED)
 */
public record SendMessageRequest(
        @Size(max = 2000, message = "Nội dung tin nhắn không được vượt quá 2000 ký tự")
        String content,

        Long mediaId,

        Long refItemId) {

    public SendMessageRequest(String content) {
        this(content, null, null);
    }

    public SendMessageRequest(String content, Long mediaId) {
        this(content, mediaId, null);
    }
}
