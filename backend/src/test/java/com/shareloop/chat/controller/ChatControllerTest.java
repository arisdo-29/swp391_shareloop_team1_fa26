package com.shareloop.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.chat.dto.MessageResponse;
import com.shareloop.chat.dto.SendMessageRequest;
import com.shareloop.chat.service.ChatService;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class ChatControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    ChatService chatService;

    private static final long REQUEST_ID = 100L;
    private static final long USER_ID = 10L;

    @Test
    @DisplayName("Gửi tin nhắn không có token trả về 401 Unauthorized")
    void sendMessage_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/requests/{requestId}/messages", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Chào bạn\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Gửi tin nhắn thành công có JWT trả về 201 Created")
    void sendMessage_withToken_returns201() throws Exception {
        String token = jwtService.issue(USER_ID, false);
        MessageResponse response = new MessageResponse(
                1L,
                REQUEST_ID,
                USER_ID,
                "TEXT",
                "Chào bạn, chiều nay lấy đồ nhé",
                null,
                null,
                null,
                "PASS",
                null,
                Instant.now());

        when(chatService.sendMessage(eq(REQUEST_ID), eq(USER_ID), any(SendMessageRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/requests/{requestId}/messages", REQUEST_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Chào bạn, chiều nay lấy đồ nhé\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("Chào bạn, chiều nay lấy đồ nhé"))
                .andExpect(jsonPath("$.senderId").value(USER_ID));
    }

    @Test
    @DisplayName("Gửi tin nhắn ảnh thành công trả về 201 Created và loại tin là IMAGE")
    void sendMessage_image_withToken_returns201() throws Exception {
        String token = jwtService.issue(USER_ID, false);
        MessageResponse response = new MessageResponse(
                2L,
                REQUEST_ID,
                USER_ID,
                "IMAGE",
                "Ảnh minh chứng giao hàng",
                42L,
                null,
                null,
                "PASS",
                null,
                Instant.now());

        when(chatService.sendMessage(eq(REQUEST_ID), eq(USER_ID), any(SendMessageRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/requests/{requestId}/messages", REQUEST_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Ảnh minh chứng giao hàng\", \"mediaId\": 42}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.kind").value("IMAGE"))
                .andExpect(jsonPath("$.mediaId").value(42))
                .andExpect(jsonPath("$.senderId").value(USER_ID));
    }

    @Test
    @DisplayName("Lấy danh sách tin nhắn thành công có JWT trả về 200 OK")
    void getMessages_withToken_returns200() throws Exception {
        String token = jwtService.issue(USER_ID, false);
        MessageResponse m1 = new MessageResponse(
                1L, REQUEST_ID, USER_ID, "TEXT", "Tin 1", null, null, null, "PASS", null, Instant.now());

        when(chatService.getMessages(eq(REQUEST_ID), eq(USER_ID), eq(null))).thenReturn(List.of(m1));

        mockMvc.perform(get("/api/v1/requests/{requestId}/messages", REQUEST_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].content").value("Tin 1"));
    }

    @Test
    @DisplayName("Polling tin nhắn với tham số afterId trả về 200 OK")
    void getMessages_withAfterId_returns200() throws Exception {
        String token = jwtService.issue(USER_ID, false);
        MessageResponse m2 = new MessageResponse(
                2L, REQUEST_ID, 20L, "TEXT", "Tin 2 mới", null, null, null, "PASS", null, Instant.now());

        when(chatService.getMessages(eq(REQUEST_ID), eq(USER_ID), eq(1L))).thenReturn(List.of(m2));

        mockMvc.perform(get("/api/v1/requests/{requestId}/messages", REQUEST_ID)
                        .param("afterId", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].content").value("Tin 2 mới"));
    }
}
