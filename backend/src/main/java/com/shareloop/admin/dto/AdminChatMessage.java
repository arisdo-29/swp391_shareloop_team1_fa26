package com.shareloop.admin.dto;

import java.time.Instant;

/**
 * Một tin nhắn trong cuộc trò chuyện của giao dịch, cho Admin đọc.
 *
 * @param senderRole Chủ bài, Người nhận hoặc Hệ thống
 * @param kind loại tin nhắn (TEXT, IMAGE, ITEM_CARD, OFFER_CHANGE, SYSTEM)
 * @param mediaUrl ảnh đính kèm, null khi không có
 * @param itemTitle tiêu đề món được nhắc tới, null khi không có
 */
public record AdminChatMessage(
        long id,
        String senderName,
        String senderRole,
        String kind,
        String content,
        String mediaUrl,
        String itemTitle,
        Instant createdAt) {}
