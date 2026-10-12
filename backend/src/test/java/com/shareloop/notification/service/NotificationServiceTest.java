package com.shareloop.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.notification.NotificationErrorCode;
import com.shareloop.notification.dto.NotificationResponse;
import com.shareloop.notification.dto.UnreadCountResponse;
import com.shareloop.notification.entity.Notification;
import com.shareloop.notification.event.NotificationEmailEvent;
import com.shareloop.notification.repository.NotificationRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository notificationRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    NotificationService notificationService;

    @Test
    @DisplayName("notify() lưu đúng thông tin thông báo vào database")
    void notify_savesNotificationSuccessfully() {
        notificationService.notify(10L, "POST_APPROVED", "Bài đã duyệt", "Bài đăng #1 đã được duyệt", "/items/1");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(10L);
        assertThat(saved.getType()).isEqualTo("POST_APPROVED");
        assertThat(saved.getTitle()).isEqualTo("Bài đã duyệt");
        assertThat(saved.getBody()).isEqualTo("Bài đăng #1 đã được duyệt");
        assertThat(saved.getLink()).isEqualTo("/items/1");
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("listMyNotifications() trả về danh sách thông báo đã map DTO")
    void listMyNotifications_returnsMappedList() {
        Notification n1 = new Notification(10L, "NEW_REQUEST", "Yêu cầu mới", "Bình xin đồ", "/requests/1");
        ReflectionTestUtils.setField(n1, "id", 100L);

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(10L), any(PageRequest.class)))
                .thenReturn(List.of(n1));

        List<NotificationResponse> responses = notificationService.listMyNotifications(10L, 0, 20);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(100L);
        assertThat(responses.get(0).title()).isEqualTo("Yêu cầu mới");
        assertThat(responses.get(0).isRead()).isFalse();
    }

    @Test
    @DisplayName("markAsRead() đánh dấu thông báo đã đọc thành công")
    void markAsRead_success() {
        Notification n = new Notification(10L, "NEW_REQUEST", "Yêu cầu mới", "Bình xin đồ", "/requests/1");
        ReflectionTestUtils.setField(n, "id", 100L);

        when(notificationRepository.findByIdAndUserId(100L, 10L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(100L, 10L);

        assertThat(response.isRead()).isTrue();
        assertThat(n.isRead()).isTrue();
        verify(notificationRepository).save(n);
    }

    @Test
    @DisplayName("markAsRead() của người khác hoặc không tồn tại ném NOTIFICATION_NOT_FOUND")
    void markAsRead_notOwnerOrNotFound_throwsException() {
        when(notificationRepository.findByIdAndUserId(100L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(100L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("getUnreadCount() trả về số lượng thông báo chưa đọc")
    void getUnreadCount_returnsCount() {
        when(notificationRepository.countByUserIdAndIsReadFalse(10L)).thenReturn(3L);

        UnreadCountResponse response = notificationService.getUnreadCount(10L);

        assertThat(response.unreadCount()).isEqualTo(3L);
        verify(notificationRepository).countByUserIdAndIsReadFalse(10L);
    }

    @Test
    @DisplayName("email() phát sự kiện NotificationEmailEvent")
    void email_publishesEvent() {
        notificationService.email("test@shareloop.local", "Tiêu đề", "welcome", Map.of("name", "An"));

        ArgumentCaptor<NotificationEmailEvent> captor = ArgumentCaptor.forClass(NotificationEmailEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        NotificationEmailEvent event = captor.getValue();
        assertThat(event.to()).isEqualTo("test@shareloop.local");
        assertThat(event.subject()).isEqualTo("Tiêu đề");
        assertThat(event.templateName()).isEqualTo("welcome");
        assertThat(event.templateModel()).containsEntry("name", "An");
    }
}
