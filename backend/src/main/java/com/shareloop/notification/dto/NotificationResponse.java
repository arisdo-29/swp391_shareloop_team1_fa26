package com.shareloop.notification.dto;

import java.time.Instant;

/**
 * DTO trả về thông tin một thông báo trong app (FR-118).
 */
public record NotificationResponse(
        Long id, String type, String title, String body, String link, boolean isRead, Instant createdAt) {}
