package com.shareloop.admin.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.admin.dto.AnomalyReportResponse;
import com.shareloop.admin.dto.AnomalyUser;
import com.shareloop.admin.dto.RepeatedPairAnomaly;
import com.shareloop.admin.service.AdminAnomalyService;
import com.shareloop.auth.service.JwtService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAnomalyController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminAnomalyControllerTest {

    private static final String URL = "/api/v1/admin/anomalies";
    private static final Instant GENERATED_AT = Instant.parse("2026-10-22T03:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    AdminAnomalyService adminAnomalyService;

    @Test
    @DisplayName("GET /admin/anomalies: chưa đăng nhập trả về 401")
    void anomalies_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(adminAnomalyService);
    }

    @Test
    @DisplayName("GET /admin/anomalies: USER không phải Admin trả về 403")
    void anomalies_nonAdmin_returns403() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(adminAnomalyService);
    }

    @Test
    @DisplayName("GET /admin/anomalies: Admin xem được cặp giao dịch lặp kèm tên hai người")
    void anomalies_admin_returnsRepeatedPairs() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        when(adminAnomalyService.getAnomalies())
                .thenReturn(new AnomalyReportResponse(
                        GENERATED_AT,
                        90,
                        5,
                        List.of(new RepeatedPairAnomaly(
                                new AnomalyUser(3L, "Nguyễn Văn An", "an@fpt.edu.vn"),
                                new AnomalyUser(7L, "Trần Thị Bình", "binh@fpt.edu.vn"),
                                6,
                                Instant.parse("2026-10-20T08:00:00Z")))));

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairWindowDays").value(90))
                .andExpect(jsonPath("$.pairThreshold").value(5))
                .andExpect(jsonPath("$.repeatedPairs.length()").value(1))
                .andExpect(jsonPath("$.repeatedPairs[0].userA.id").value(3))
                .andExpect(jsonPath("$.repeatedPairs[0].userA.fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.repeatedPairs[0].userB.id").value(7))
                .andExpect(jsonPath("$.repeatedPairs[0].completedCount").value(6))
                .andExpect(jsonPath("$.repeatedPairs[0].lastCompletedAt").value("2026-10-20T08:00:00Z"));
    }

    @Test
    @DisplayName("GET /admin/anomalies: không có cảnh báo nào thì trả 200 với danh sách rỗng")
    void anomalies_noAnomaly_returnsEmptyList() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        when(adminAnomalyService.getAnomalies()).thenReturn(new AnomalyReportResponse(GENERATED_AT, 90, 5, List.of()));

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repeatedPairs").isArray())
                .andExpect(jsonPath("$.repeatedPairs.length()").value(0));
    }
}
