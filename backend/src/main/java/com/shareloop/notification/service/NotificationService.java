package com.shareloop.notification.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.notification.NotificationErrorCode;
import com.shareloop.notification.dto.NotificationResponse;
import com.shareloop.notification.dto.UnreadCountResponse;
import com.shareloop.notification.entity.Notification;
import com.shareloop.notification.event.NotificationEmailEvent;
import com.shareloop.notification.mapper.NotificationMapper;
import com.shareloop.notification.repository.NotificationRepository;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service cửa ngõ cho thông báo trong ứng dụng và gửi email giao dịch (Lộ trình 4.7, SRS mục 9.15).
 *
 * <p>Mọi module khác gọi gửi thông báo (chuông) hoặc gửi email thông báo qua service này.
 *
 * <p>Người gọi: mọi module (auth, item, review, request, report, reputation).
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final com.shareloop.user.service.UserService userService;

    public NotificationService(
            NotificationRepository notificationRepository, ApplicationEventPublisher eventPublisher) {
        this(notificationRepository, eventPublisher, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationService(
            NotificationRepository notificationRepository,
            ApplicationEventPublisher eventPublisher,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    com.shareloop.user.service.UserService userService) {
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
        this.userService = userService;
    }

    /**
     * Tạo thông báo trong app (hiển thị ở icon chuông thông báo).
     *
     * @param userId người nhận thông báo
     * @param type loại thông báo (POST_APPROVED, NEW_REQUEST, SELECTED, LOGISTICS_CONFIRMED, ...)
     * @param title tiêu đề thông báo
     * @param body nội dung thông báo
     * @param link đường dẫn chuyển hướng trong frontend (có thể null)
     */
    @Transactional
    public void notify(long userId, String type, String title, String body, String link) {
        Notification notification = new Notification(userId, type, title, body, link);
        notificationRepository.save(notification);
    }

    /**
     * Gửi thông báo cảnh báo tới tất cả các Admin đang hoạt động trong hệ thống (FR-76, BR-C04).
     *
     * @param type loại thông báo (ví dụ CHAT_VIOLATION_ALERT)
     * @param title tiêu đề cảnh báo
     * @param body nội dung cảnh báo chi tiết
     * @param link liên kết điều hướng
     */
    @Transactional
    public void notifyAdmins(String type, String title, String body, String link) {
        if (userService == null) {
            return;
        }
        List<Long> adminIds = userService.findAdminIds();
        if (adminIds != null) {
            for (Long adminId : adminIds) {
                notify(adminId, type, title, body, link);
            }
        }
    }

    /**
     * Gửi email theo template HTML (sau commit qua EmailSender).
     *
     * @param to địa chỉ email người nhận
     * @param subject tiêu đề email
     * @param templateName tên template trong resources/templates/mail/ (không gồm đuôi .html)
     * @param templateModel dữ liệu cần điền vào template
     */
    public void email(String to, String subject, String templateName, Map<String, Object> templateModel) {
        eventPublisher.publishEvent(new NotificationEmailEvent(to, subject, templateName, templateModel));
    }

    /**
     * Xem danh sách thông báo của tôi (phân trang, sắp theo thời gian tạo mới nhất lên trước).
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> listMyNotifications(long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = (size <= 0 || size > 100) ? 20 : size;
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(safePage, safeSize))
                .stream()
                .map(NotificationMapper::toResponse)
                .toList();
    }

    /**
     * Đánh dấu một thông báo đã đọc.
     */
    @Transactional
    public NotificationResponse markAsRead(long id, long userId) {
        Notification notification = notificationRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        notification.markAsRead();
        Notification saved = notificationRepository.save(notification);
        return NotificationMapper.toResponse(saved);
    }

    /**
     * Đếm số lượng thông báo chưa đọc của người dùng.
     */
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(long userId) {
        long count = notificationRepository.countByUserIdAndIsReadFalse(userId);
        return new UnreadCountResponse(count);
    }
}
