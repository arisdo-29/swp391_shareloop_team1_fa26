package com.shareloop.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.chat.entity.Message;
import com.shareloop.chat.entity.MessageModeration;
import com.shareloop.chat.repository.MessageRepository;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test cho việc ghi nhận vi phạm chat và ngưỡng cảnh báo Admin (FR-76, FR-78; BR-C04).
 */
@ExtendWith(MockitoExtension.class)
class ChatViolationRecorderTest {

    @Mock
    MessageRepository messageRepository;

    @Mock
    NotificationService notificationService;

    @Mock
    ConfigService configService;

    @InjectMocks
    ChatViolationRecorder violationRecorder;

    private static final long REQUEST_ID = 200L;
    private static final long SENDER_ID = 15L;

    @Test
    @DisplayName("Lưu tin nhắn bị chặn vào database với trạng thái BLOCKED và tier 1")
    void recordViolation_savesBlockedMessage() {
        when(configService.getInt(ConfigKey.CHAT_VIOLATION_ALERT)).thenReturn(3);
        when(messageRepository.countByRequestIdAndModeration(REQUEST_ID, MessageModeration.BLOCKED))
                .thenReturn(1L);

        long count = violationRecorder.recordViolationAndAlertAdmin(
                REQUEST_ID, SENDER_ID, "alo sdt 0912345678", "số điện thoại (0912345678)");

        assertThat(count).isEqualTo(1L);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());

        Message saved = messageCaptor.getValue();
        assertThat(saved.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(saved.getSenderId()).isEqualTo(SENDER_ID);
        assertThat(saved.getContent()).isEqualTo("alo sdt 0912345678");
        assertThat(saved.getModeration()).isEqualTo(MessageModeration.BLOCKED);
        assertThat(saved.getModerationTier()).isEqualTo((short) 1);
        assertThat(saved.getBlockedReason()).isEqualTo("số điện thoại (0912345678)");
    }

    @Test
    @DisplayName("Khi số vi phạm dưới ngưỡng (1 hoặc 2 < 3) thì KHÔNG cảnh báo Admin")
    void recordViolation_belowThreshold_doesNotAlertAdmin() {
        when(configService.getInt(ConfigKey.CHAT_VIOLATION_ALERT)).thenReturn(3);
        when(messageRepository.countByRequestIdAndModeration(REQUEST_ID, MessageModeration.BLOCKED))
                .thenReturn(2L);

        long count = violationRecorder.recordViolationAndAlertAdmin(
                REQUEST_ID, SENDER_ID, "vi phạm lần 2", "từ khoá cấm (cần sa)");

        assertThat(count).isEqualTo(2L);
        verify(notificationService, never()).notifyAdmins(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Khi số vi phạm đạt ngưỡng (3 lần) thì gửi cảnh báo tới Admin qua NotificationService (BR-C04, FR-76)")
    void recordViolation_reachesThreshold_alertsAdmin() {
        when(configService.getInt(ConfigKey.CHAT_VIOLATION_ALERT)).thenReturn(3);
        when(messageRepository.countByRequestIdAndModeration(REQUEST_ID, MessageModeration.BLOCKED))
                .thenReturn(3L);

        long count = violationRecorder.recordViolationAndAlertAdmin(
                REQUEST_ID, SENDER_ID, "vi phạm lần 3", "thông tin liên hệ: email");

        assertThat(count).isEqualTo(3L);
        verify(notificationService)
                .notifyAdmins(
                        eq("CHAT_VIOLATION_ALERT"),
                        eq("Cảnh báo vi phạm chat trong yêu cầu #200"),
                        eq("Yêu cầu giao dịch #200 đã có 3 tin nhắn bị chặn do vi phạm quy định."),
                        eq("/requests/200"));
    }

    @Test
    @DisplayName("Khi số vi phạm vượt ngưỡng (4 lần > 3) thì tiếp tục gửi cảnh báo tới Admin")
    void recordViolation_exceedsThreshold_alertsAdmin() {
        when(configService.getInt(ConfigKey.CHAT_VIOLATION_ALERT)).thenReturn(3);
        when(messageRepository.countByRequestIdAndModeration(REQUEST_ID, MessageModeration.BLOCKED))
                .thenReturn(4L);

        long count = violationRecorder.recordViolationAndAlertAdmin(
                REQUEST_ID, SENDER_ID, "vi phạm lần 4", "thông tin liên hệ: link");

        assertThat(count).isEqualTo(4L);
        verify(notificationService)
                .notifyAdmins(
                        eq("CHAT_VIOLATION_ALERT"),
                        eq("Cảnh báo vi phạm chat trong yêu cầu #200"),
                        eq("Yêu cầu giao dịch #200 đã có 4 tin nhắn bị chặn do vi phạm quy định."),
                        eq("/requests/200"));
    }

    @Test
    @DisplayName("Ngưỡng đọc động từ ConfigService (ví dụ ngưỡng 5)")
    void recordViolation_customThresholdFromConfig() {
        when(configService.getInt(ConfigKey.CHAT_VIOLATION_ALERT)).thenReturn(5);
        when(messageRepository.countByRequestIdAndModeration(REQUEST_ID, MessageModeration.BLOCKED))
                .thenReturn(4L);

        long count = violationRecorder.recordViolationAndAlertAdmin(
                REQUEST_ID, SENDER_ID, "vi phạm lần 4", "từ khoá cấm (súng)");

        assertThat(count).isEqualTo(4L);
        // Ngưỡng 5 mà mới có 4 lần -> chưa gửi cảnh báo
        verify(notificationService, never()).notifyAdmins(any(), any(), any(), any());
    }
}
