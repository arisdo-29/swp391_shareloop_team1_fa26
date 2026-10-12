package com.shareloop.notification.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.notification.dto.NotificationResponse;
import com.shareloop.notification.dto.UnreadCountResponse;
import com.shareloop.notification.service.NotificationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    NotificationService notificationService;

    @Test
    @DisplayName("Xem thông báo không có JWT trả về 401 Unauthorized")
    void listMyNotifications_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Xem thông báo có JWT trả về 200 OK và danh sách thông báo")
    void listMyNotifications_withToken_returns200() throws Exception {
        String token = jwtService.issue(10L, false);

        NotificationResponse response =
                new NotificationResponse(1L, "NEW_REQUEST", "Tiêu đề", "Nội dung", "/requests/1", false, Instant.now());

        when(notificationService.listMyNotifications(eq(10L), anyInt(), anyInt()))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].type").value("NEW_REQUEST"))
                .andExpect(jsonPath("$[0].title").value("Tiêu đề"))
                .andExpect(jsonPath("$[0].isRead").value(false));
    }

    @Test
    @DisplayName("Đánh dấu đã đọc thành công trả về 200 OK")
    void markAsRead_withToken_returns200() throws Exception {
        String token = jwtService.issue(10L, false);

        NotificationResponse response =
                new NotificationResponse(1L, "NEW_REQUEST", "Tiêu đề", "Nội dung", "/requests/1", true, Instant.now());

        when(notificationService.markAsRead(1L, 10L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/notifications/1/read").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.isRead").value(true));
    }

    @Test
    @DisplayName("Lấy số lượng chưa đọc trả về 200 OK")
    void getUnreadCount_withToken_returns200() throws Exception {
        String token = jwtService.issue(10L, false);

        when(notificationService.getUnreadCount(10L)).thenReturn(new UnreadCountResponse(4L));

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(4L));
    }
}
