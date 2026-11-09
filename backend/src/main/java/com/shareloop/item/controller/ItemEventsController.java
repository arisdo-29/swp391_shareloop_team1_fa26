package com.shareloop.item.controller;

import com.shareloop.notification.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Tín hiệu thay đổi bài công khai cho trang chủ và trang tìm đồ. */
@RestController
public class ItemEventsController {
    private final NotificationService notificationService;

    public ItemEventsController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping(path = "/api/v1/items/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return notificationService.subscribeCatalog();
    }
}
