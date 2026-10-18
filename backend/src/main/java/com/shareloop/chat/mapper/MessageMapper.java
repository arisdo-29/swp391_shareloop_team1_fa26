package com.shareloop.chat.mapper;

import com.shareloop.chat.dto.MessageResponse;
import com.shareloop.chat.entity.Message;
import com.shareloop.item.dto.ItemCardDto;

/**
 * Mapper chuyển đổi giữa entity Message và DTO MessageResponse.
 */
public final class MessageMapper {

    private MessageMapper() {}

    public static MessageResponse toResponse(Message message) {
        return toResponse(message, null);
    }

    public static MessageResponse toResponse(Message message, ItemCardDto itemCard) {
        if (message == null) {
            return null;
        }
        return new MessageResponse(
                message.getId(),
                message.getRequestId(),
                message.getSenderId(),
                message.getKind() != null ? message.getKind().name() : null,
                message.getContent(),
                message.getMediaId(),
                message.getRefItemId(),
                itemCard,
                message.getOfferStatus(),
                message.getModeration() != null ? message.getModeration().name() : null,
                message.getBlockedReason(),
                message.getCreatedAt());
    }
}
