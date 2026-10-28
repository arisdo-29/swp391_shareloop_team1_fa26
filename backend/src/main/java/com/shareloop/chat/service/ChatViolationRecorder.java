package com.shareloop.chat.service;

import com.shareloop.chat.entity.Message;
import com.shareloop.chat.entity.MessageModeration;
import com.shareloop.chat.repository.MessageRepository;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.ContactInfo;
import com.shareloop.user.service.UserService;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi nhận vi phạm chat và cảnh báo Admin khi số vi phạm trong một Request đạt ngưỡng (FR-76, FR-78; BR-C04).
 *
 * <p>Chạy trong transaction {@link Propagation#REQUIRES_NEW} độc lập để đảm bảo dòng tin nhắn bị chặn
 * và thông báo tới Admin được lưu xuống cơ sở dữ liệu ngay cả khi luồng chính ném lỗi nghiệp vụ CHAT_MESSAGE_BLOCKED.
 */
@Component
public class ChatViolationRecorder {

    private final MessageRepository messageRepository;
    private final NotificationService notificationService;
    private final ConfigService configService;
    private final UserService userService;
    private final JdbcClient jdbcClient;

    @Autowired
    public ChatViolationRecorder(
            MessageRepository messageRepository,
            NotificationService notificationService,
            @Autowired(required = false) ConfigService configService,
            @Autowired(required = false) UserService userService,
            @Autowired(required = false) JdbcClient jdbcClient) {
        this.messageRepository = messageRepository;
        this.notificationService = notificationService;
        this.configService = configService;
        this.userService = userService;
        this.jdbcClient = jdbcClient;
    }

    public ChatViolationRecorder(
            MessageRepository messageRepository, NotificationService notificationService, ConfigService configService) {
        this(messageRepository, notificationService, configService, null, null);
    }

    /**
     * Ghi nhận một tin nhắn bị chặn vào cơ sở dữ liệu và gửi cảnh báo tới Admin nếu số vi phạm trong giao dịch
     * đạt hoặc vượt ngưỡng cấu hình (FR-76, BR-C04).
     *
     * @param requestId id của yêu cầu giao dịch
     * @param senderId  id người gửi vi phạm
     * @param content   nội dung tin nhắn vi phạm
     * @param reason    lý do tin nhắn bị chặn
     * @return tổng số tin nhắn bị chặn trong yêu cầu này sau khi ghi nhận
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long recordViolationAndAlertAdmin(long requestId, long senderId, String content, String reason) {
        return recordViolationAndAlertAdmin(requestId, senderId, content, reason, 1, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long recordViolationAndAlertAdmin(
            long requestId, long senderId, String content, String reason, int tier, BigDecimal confidence) {
        Message message = new Message(requestId, senderId, content);
        message.block(tier, reason);
        if (confidence != null) {
            message.setAiConfidence(confidence);
        }
        messageRepository.save(message);

        long violationCount = messageRepository.countByRequestIdAndModeration(requestId, MessageModeration.BLOCKED);

        int threshold = 3;
        if (configService != null) {
            threshold = configService.getInt(ConfigKey.CHAT_VIOLATION_ALERT);
        }

        if (violationCount >= threshold && notificationService != null) {
            String senderName = resolveSenderName(senderId);
            String itemTitle = resolveItemTitle(requestId);
            String title = "Cảnh báo vi phạm chat: " + senderName + " (" + itemTitle + ")";
            String body = senderName + " có " + violationCount
                    + " tin nhắn bị chặn do vi phạm quy định trong giao dịch món \"" + itemTitle + "\".";
            String link = "/admin/users";
            notificationService.notifyAdmins("CHAT_VIOLATION_ALERT", title, body, link);
        }

        return violationCount;
    }

    private String resolveSenderName(long senderId) {
        if (userService != null) {
            try {
                ContactInfo contact = userService.findContact(senderId);
                if (contact != null
                        && contact.fullName() != null
                        && !contact.fullName().isBlank()) {
                    return contact.fullName();
                }
            } catch (Exception ignored) {
            }
        }
        return "Người dùng #" + senderId;
    }

    private String resolveItemTitle(long requestId) {
        if (jdbcClient != null) {
            try {
                return jdbcClient
                        .sql("SELECT i.title FROM requests r JOIN items i ON i.id = r.item_id WHERE r.id = :requestId")
                        .param("requestId", requestId)
                        .query(String.class)
                        .optional()
                        .orElse("yêu cầu #" + requestId);
            } catch (Exception ignored) {
            }
        }
        return "yêu cầu #" + requestId;
    }
}
