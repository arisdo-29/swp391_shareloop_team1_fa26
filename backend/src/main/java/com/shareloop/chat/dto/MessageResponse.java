package com.shareloop.chat.dto;

import java.time.Instant;

/**
 * DTO phản hồi thông tin của một tin nhắn trong khung chat (FR-68).
 */
public record MessageResponse(
        Long id,
        Long requestId,
        Long senderId,
        String kind,
        String content,
        Long mediaId,
        Long refItemId,
        String offerStatus,
        String moderation,
        String blockedReason,
        Instant createdAt) {}
