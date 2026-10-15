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
import com.shareloop.request.dto.RequestContactResponse;
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

    @Test
    @DisplayName("Chủ bài chọn người nhận thành công trả về 200 OK")
    void select_withToken_returns200() throws Exception {
        String token = jwtService.issue(20L, false);

        RequestResponse response =
                new RequestResponse(100L, 1L, 10L, null, "GIVE", "RESERVED", null, null, null, Instant.now());

        when(requestService.selectReceiver(100L, 20L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/requests/100/select").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    @DisplayName("Xem chi tiết request có JWT trả về 200 OK")
    void getDetail_withToken_returns200() throws Exception {
        String token = jwtService.issue(10L, false);

        RequestResponse response =
                new RequestResponse(100L, 1L, 10L, null, "GIVE", "RESERVED", null, null, null, Instant.now());

        when(requestService.getDetail(100L, 10L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/requests/100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.receiverId").value(10));
    }

    @Test
    @DisplayName("Xem chi tiết request không có JWT trả về 401 Unauthorized")
    void getDetail_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/requests/100"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Đề xuất lịch hẹn thành công trả về 200 OK")
    void updateSchedule_withToken_returns200() throws Exception {
        String token = jwtService.issue(20L, false);

        Instant meetingTime = Instant.parse("2026-10-25T10:00:00Z");
        RequestResponse response = new RequestResponse(
                100L,
                1L,
                10L,
                null,
                "GIVE",
                "AWAITING_LOGISTICS",
                "IN_PERSON",
                "Cổng trường FPT",
                meetingTime,
                Instant.now(),
                null,
                null,
                null,
                null,
                Instant.now());

        when(requestService.updateSchedule(eq(100L), eq(20L), any())).thenReturn(response);

        String jsonBody = """
                {
                    "deliveryMethod": "IN_PERSON",
                    "meetingPlace": "Cổng trường FPT",
                    "meetingTime": "2026-10-25T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/requests/100/schedule")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_LOGISTICS"))
                .andExpect(jsonPath("$.deliveryMethod").value("IN_PERSON"))
                .andExpect(jsonPath("$.meetingPlace").value("Cổng trường FPT"));
    }

    @Test
    @DisplayName("Đề xuất lịch hẹn thiếu trường hoặc sai deliveryMethod trả về 400 Bad Request")
    void updateSchedule_invalidBody_returns400() throws Exception {
        String token = jwtService.issue(20L, false);

        String invalidBody = """
                {
                    "deliveryMethod": "INVALID_METHOD",
                    "meetingPlace": ""
                }
                """;

        mockMvc.perform(post("/api/v1/requests/100/schedule")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Xác nhận lịch hẹn có JWT trả về 200 OK")
    void confirmLogistics_withToken_returns200() throws Exception {
        String token = jwtService.issue(10L, false);

        Instant meetingTime = Instant.parse("2026-10-25T10:00:00Z");
        RequestResponse response = new RequestResponse(
                100L,
                1L,
                10L,
                null,
                "GIVE",
                "LOGISTICS_CONFIRMED",
                "IN_PERSON",
                "Cổng trường FPT",
                meetingTime,
                Instant.now(),
                Instant.now(),
                null,
                null,
                Instant.now(),
                Instant.now());

        when(requestService.confirmLogistics(eq(100L), eq(10L), any())).thenReturn(response);

        String jsonBody = """
                {
                    "agreeToWaiver": true
                }
                """;

        mockMvc.perform(post("/api/v1/requests/100/confirm-logistics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGISTICS_CONFIRMED"))
                .andExpect(jsonPath("$.contactRevealedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Lấy thông tin liên lạc đối tác có JWT trả về 200 OK")
    void getContact_withToken_returns200() throws Exception {
        String token = jwtService.issue(10L, false);

        RequestContactResponse response = new RequestContactResponse(20L, "Chủ Bài", "donor@example.com", "0987654321");

        when(requestService.getContactInfo(100L, 10L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/requests/100/contact").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(20))
                .andExpect(jsonPath("$.fullName").value("Chủ Bài"))
                .andExpect(jsonPath("$.email").value("donor@example.com"))
                .andExpect(jsonPath("$.phone").value("0987654321"));
    }

    @Test
    @DisplayName("Lấy thông tin liên lạc đối tác không có JWT trả về 401 Unauthorized")
    void getContact_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/requests/100/contact"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Xác nhận giao nhận (Handover) có JWT trả về 200 OK")
    void confirmHandover_withToken_returns200() throws Exception {
        String token = jwtService.issue(10L, false);

        RequestResponse response = new RequestResponse(
                100L,
                1L,
                10L,
                null,
                "GIVE",
                "COMPLETED",
                "IN_PERSON",
                "Cổng trường FPT",
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null,
                null,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                true,
                Instant.now(),
                Instant.now());

        when(requestService.confirmHandover(eq(100L), eq(10L), any())).thenReturn(response);

        String jsonBody = """
                {
                    "asDescribed": true
                }
                """;

        mockMvc.perform(post("/api/v1/requests/100/confirm-handover")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.asDescribed").value(true))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Xác nhận giao nhận (Handover) không có JWT trả về 401 Unauthorized")
    void confirmHandover_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/requests/100/confirm-handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
