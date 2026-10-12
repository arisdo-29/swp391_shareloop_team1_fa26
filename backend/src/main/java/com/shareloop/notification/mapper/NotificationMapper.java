package com.shareloop.notification.mapper;

import com.shareloop.notification.dto.NotificationResponse;
import com.shareloop.notification.entity.Notification;

public final class NotificationMapper {

    private NotificationMapper() {}

    public static NotificationResponse toResponse(Notification notification) {
        if (notification == null) {
            return null;
        }
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLink(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
