package com.shareloop.chat.dto;

import com.shareloop.item.dto.ItemCardDto;
import java.time.Instant;

/**
 * DTO phản hồi thông tin của một tin nhắn trong khung chat (FR-68, FR-70).
 */
public record MessageResponse(
        Long id,
        Long requestId,
        Long senderId,
        String kind,
        String content,
        Long mediaId,
        Long refItemId,
        ItemCardDto itemCard,
        String offerStatus,
        String moderation,
        String blockedReason,
        Instant createdAt) {

    public MessageResponse(
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
            Instant createdAt) {
        this(
                id,
                requestId,
                senderId,
                kind,
                content,
                mediaId,
                refItemId,
                null,
                offerStatus,
                moderation,
                blockedReason,
                createdAt);
    }
}
