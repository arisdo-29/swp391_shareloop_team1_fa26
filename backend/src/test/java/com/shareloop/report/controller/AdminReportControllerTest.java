package com.shareloop.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.ConcludeReportRequest;
import com.shareloop.report.dto.ReportConclusionResponse;
import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.service.ReportService;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminReportController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminReportControllerTest {

    private static final String BASE_URL = "/api/v1/admin/reports";
    private static final long ADMIN_ID = 1L;
    private static final long NORMAL_USER_ID = 2L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    ReportService reportService;

    @Test
    @DisplayName("Kết luận khi chưa đăng nhập trả về 401 Unauthorized")
    void conclude_withoutToken_returns401() throws Exception {
        mockMvc.perform(post(BASE_URL + "/1/conclude")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"UPHOLD\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Người dùng thông thường gọi endpoint Admin trả về 403 Forbidden")
    void conclude_byNormalUser_returns403() throws Exception {
        String token = jwtService.issue(NORMAL_USER_ID, false);

        mockMvc.perform(post(BASE_URL + "/1/conclude")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"UPHOLD\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Thiếu decision trong body trả về 400 Bad Request")
    void conclude_missingDecision_returns400() throws Exception {
        String adminToken = jwtService.issue(ADMIN_ID, true);

        mockMvc.perform(post(BASE_URL + "/1/conclude")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Ghi chu\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Admin kết luận UPHOLD thành công trả về 200 OK và dữ liệu phản hồi")
    void conclude_uphold_success() throws Exception {
        String adminToken = jwtService.issue(ADMIN_ID, true);

        ReportConclusionResponse response = new ReportConclusionResponse(
                1L,
                TargetType.ITEM,
                105L,
                null,
                null,
                ReportReason.PROHIBITED_ITEM,
                ReportStatus.RESOLVED,
                ADMIN_ID,
                "Hàng cấm theo quy định",
                (short) 1,
                Instant.parse("2026-10-18T13:00:00Z"));

        when(reportService.conclude(eq(ADMIN_ID), eq(1L), any(ConcludeReportRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/1/conclude")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "decision": "UPHOLD",
                          "note": "Hàng cấm theo quy định"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedBy").value(ADMIN_ID))
                .andExpect(jsonPath("$.starsDeducted").value(1))
                .andExpect(jsonPath("$.resolution").value("Hàng cấm theo quy định"));
    }

    @Test
    @DisplayName("Admin kết luận DISMISS thành công trả về 200 OK")
    void conclude_dismiss_success() throws Exception {
        String adminToken = jwtService.issue(ADMIN_ID, true);

        ReportConclusionResponse response = new ReportConclusionResponse(
                2L,
                TargetType.USER,
                null,
                20L,
                null,
                ReportReason.HARASSMENT,
                ReportStatus.DISMISSED,
                ADMIN_ID,
                "Không đủ bằng chứng",
                (short) 0,
                Instant.parse("2026-10-18T13:00:00Z"));

        when(reportService.conclude(eq(ADMIN_ID), eq(2L), any(ConcludeReportRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/2/conclude")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "decision": "DISMISS",
                          "note": "Không đủ bằng chứng"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.starsDeducted").value(0));
    }

    @Test
    @DisplayName("Báo cáo không tồn tại trả về 404 Not Found")
    void conclude_notFound_returns404() throws Exception {
        String adminToken = jwtService.issue(ADMIN_ID, true);

        when(reportService.conclude(eq(ADMIN_ID), eq(999L), any(ConcludeReportRequest.class)))
                .thenThrow(new BusinessException(ReportErrorCode.REPORT_NOT_FOUND));

        mockMvc.perform(post(BASE_URL + "/999/conclude")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"UPHOLD\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    @DisplayName("Báo cáo đã kết luận trước đó trả về 409 Conflict")
    void conclude_alreadyConcluded_returns409() throws Exception {
        String adminToken = jwtService.issue(ADMIN_ID, true);

        when(reportService.conclude(eq(ADMIN_ID), eq(1L), any(ConcludeReportRequest.class)))
                .thenThrow(new BusinessException(ReportErrorCode.REPORT_ALREADY_CONCLUDED));

        mockMvc.perform(post(BASE_URL + "/1/conclude")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"UPHOLD\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_CONCLUDED"));
    }
}
