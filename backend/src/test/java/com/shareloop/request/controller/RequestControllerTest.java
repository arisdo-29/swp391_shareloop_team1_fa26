package com.shareloop.request.controller;

import static org.mockito.ArgumentMatchers.any;
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
import com.shareloop.request.dto.CreateRequest;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.service.RequestService;
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

@WebMvcTest(RequestController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class RequestControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    RequestService requestService;

    @Test
    @DisplayName("Gửi yêu cầu không có JWT trả về 401 Unauthorized")
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/items/1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Gửi yêu cầu thành công có JWT trả về 201 Created")
    void create_withToken_returns201() throws Exception {
        String token = jwtService.issue(10L, false);

        RequestResponse response =
                new RequestResponse(100L, 1L, 10L, null, "GIVE", "PENDING", null, null, null, Instant.now());

        when(requestService.createRequest(eq(1L), eq(10L), any(CreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/items/1/requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredItemId\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.itemId").value(1))
                .andExpect(jsonPath("$.receiverId").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("Xem danh sách yêu cầu của bài đăng trả về 200 OK")
    void listByItem_withToken_returns200() throws Exception {
        String token = jwtService.issue(99L, false);

        when(requestService.listByItem(1L, 99L))
                .thenReturn(List.of(
                        new RequestResponse(100L, 1L, 10L, null, "GIVE", "PENDING", null, null, null, Instant.now())));

        mockMvc.perform(get("/api/v1/items/1/requests").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100));
    }

    @Test
    @DisplayName("Xem danh sách yêu cầu của tôi trả về 200 OK")
    void listMyRequests_withToken_returns200() throws Exception {
        String token = jwtService.issue(10L, false);

        when(requestService.listMyRequests(10L))
                .thenReturn(List.of(
                        new RequestResponse(100L, 1L, 10L, null, "GIVE", "PENDING", null, null, null, Instant.now())));

        mockMvc.perform(get("/api/v1/me/requests").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100));
    }

    @Test
    @DisplayName("Người gửi tự huỷ yêu cầu thành công trả về 200 OK")
    void cancel_withToken_returns200() throws Exception {
        String token = jwtService.issue(10L, false);

        RequestResponse response =
                new RequestResponse(100L, 1L, 10L, null, "GIVE", "CANCELLED", null, null, null, Instant.now());

        when(requestService.cancelRequest(100L, 10L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/requests/100/cancel").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
