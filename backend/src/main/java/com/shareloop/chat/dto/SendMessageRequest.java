package com.shareloop.chat.dto;

import jakarta.validation.constraints.Size;

/**
 * Yêu cầu gửi tin nhắn văn bản hoặc ảnh vào cuộc trò chuyện (FR-68, FR-75).
 *
 * @param content nội dung tin nhắn gửi đi (tuỳ chọn nếu gửi ảnh)
 * @param mediaId id của ảnh đã tải lên từ MediaService (tuỳ chọn)
 */
public record SendMessageRequest(
        @Size(max = 2000, message = "Nội dung tin nhắn không được vượt quá 2000 ký tự")
        String content,

        Long mediaId) {

    public SendMessageRequest(String content) {
        this(content, null);
    }
}
