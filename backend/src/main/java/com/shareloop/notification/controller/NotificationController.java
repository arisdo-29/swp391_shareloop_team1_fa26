package com.shareloop.notification.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.notification.dto.NotificationResponse;
import com.shareloop.notification.dto.UnreadCountResponse;
import com.shareloop.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller quản lý thông báo trong app (FR-118, UC-20).
 */
@Tag(name = "Notification", description = "Quản lý thông báo trong ứng dụng")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Xem danh sách thông báo của tôi (phân trang, mới nhất lên trước).
     */
    @Operation(summary = "Xem danh sách thông báo của tôi (phân trang)")
    @GetMapping
    public List<NotificationResponse> listMyNotifications(
            @Parameter(hidden = true) @CurrentUserId Long currentUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.listMyNotifications(currentUserId, page, size);
    }

    /**
     * Đánh dấu một thông báo đã đọc.
     */
    @Operation(summary = "Đánh dấu thông báo đã đọc")
    @PostMapping("/{id}/read")
    public NotificationResponse markAsRead(
            @PathVariable Long id, @Parameter(hidden = true) @CurrentUserId Long currentUserId) {
        return notificationService.markAsRead(id, currentUserId);
    }

    /**
     * Lấy số lượng thông báo chưa đọc (dùng cho icon chuông thông báo).
     */
    @Operation(summary = "Lấy số lượng thông báo chưa đọc")
    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCount(@Parameter(hidden = true) @CurrentUserId Long currentUserId) {
        return notificationService.getUnreadCount(currentUserId);
    }
}
