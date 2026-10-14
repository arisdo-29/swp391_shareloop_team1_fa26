package com.shareloop.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu gửi tin nhắn văn bản vào cuộc trò chuyện (FR-68).
 *
 * @param content nội dung tin nhắn gửi đi
 */
public record SendMessageRequest(
        @NotBlank(message = "Nội dung tin nhắn không được để trống")
        @Size(max = 2000, message = "Nội dung tin nhắn không được vượt quá 2000 ký tự")
        String content) {}
