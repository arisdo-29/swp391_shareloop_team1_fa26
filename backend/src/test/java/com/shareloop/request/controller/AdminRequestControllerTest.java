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
import com.shareloop.request.dto.AdminRequestPageResponse;
import com.shareloop.request.dto.AdminRequestRow;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.service.AdminRequestService;
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

@WebMvcTest(AdminRequestController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminRequestControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    AdminRequestService adminRequestService;

    @Test
    @DisplayName("Danh sách giao dịch của Admin trả về 200 với phân trang và lý do gắn cờ")
    void list_asAdmin_returns200() throws Exception {
        String token = jwtService.issue(1L, true);
        AdminRequestRow row = new AdminRequestRow(
                5L,
                1L,
                "Xe đạp",
                "GIVE",
                "LOGISTICS_CONFIRMED",
                20L,
                "An",
                10L,
                "Bình",
                Instant.now(),
                Instant.now(),
                "Quá giờ hẹn");
        when(adminRequestService.list(RequestStatus.LOGISTICS_CONFIRMED, true, "xe", 0, 20))
                .thenReturn(new AdminRequestPageResponse(List.of(row), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/admin/requests")
                        .param("status", "LOGISTICS_CONFIRMED")
                        .param("stuck", "true")
                        .param("q", "xe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(5))
                .andExpect(jsonPath("$.items[0].flagReason").value("Quá giờ hẹn"))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("Người dùng thường vào danh sách giao dịch Admin bị 403")
    void list_asNormalUser_returns403() throws Exception {
        String token = jwtService.issue(10L, false);

        mockMvc.perform(get("/api/v1/admin/requests").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Không có token vào danh sách giao dịch Admin bị 401")
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/requests")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin huỷ giao dịch với lý do hợp lệ trả về 200")
    void cancel_asAdmin_returns200() throws Exception {
        String token = jwtService.issue(1L, true);
        when(adminRequestService.cancel(eq(1L), eq(5L), any()))
                .thenReturn(
                        new RequestResponse(5L, 1L, 10L, null, "GIVE", "CANCELLED", null, null, null, Instant.now()));

        mockMvc.perform(post("/api/v1/admin/requests/5/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Hai bên không liên lạc được\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Admin huỷ giao dịch với lý do quá ngắn trả về 400")
    void cancel_withShortReason_returns400() throws Exception {
        String token = jwtService.issue(1L, true);

        mockMvc.perform(post("/api/v1/admin/requests/5/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ab\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Người dùng thường huỷ qua đường Admin bị 403")
    void cancel_asNormalUser_returns403() throws Exception {
        String token = jwtService.issue(10L, false);

        mockMvc.perform(post("/api/v1/admin/requests/5/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Hai bên không liên lạc được\"}"))
                .andExpect(status().isForbidden());
    }
}
