package com.shareloop.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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
import com.shareloop.report.dto.CreateDisputeRequest;
import com.shareloop.report.dto.DisputeResponse;
import com.shareloop.report.dto.DisputeResponseRequest;
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

@WebMvcTest(DisputeController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class DisputeControllerTest {

    private static final String DISPUTE_OPEN_URL = "/api/v1/requests/100/disputes";
    private static final String DISPUTE_RESPONSE_URL = "/api/v1/disputes/1/response";
    private static final long USER_ID = 10L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    ReportService reportService;

    @Test
    @DisplayName("Khiếu nại khi chưa đăng nhập trả về 401 Unauthorized")
    void openDispute_withoutToken_returns401() throws Exception {
        mockMvc.perform(post(DISPUTE_OPEN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NOT_AS_DESCRIBED\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Khiếu nại hợp lệ trả về 201 Created và thông tin hạn chót giải trình")
    void openDispute_valid_returns201() throws Exception {
        String token = jwtService.issue(USER_ID, false);
        Instant responseDueAt = Instant.parse("2026-10-21T12:00:00Z");

        when(reportService.openDispute(eq(USER_ID), eq(100L), any(CreateDisputeRequest.class)))
                .thenReturn(new DisputeResponse(1L, responseDueAt));

        mockMvc.perform(post(DISPUTE_OPEN_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "reason": "NOT_AS_DESCRIBED",
                          "note": "Áo bị rách",
                          "evidenceMediaIds": [10, 11]
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.responseDueAt").value(responseDueAt.toString()));
    }

    @Test
    @DisplayName("Người ngoài giao dịch khiếu nại trả về 403 Forbidden")
    void openDispute_notParty_returns403() throws Exception {
        String token = jwtService.issue(USER_ID, false);

        when(reportService.openDispute(eq(USER_ID), eq(100L), any(CreateDisputeRequest.class)))
                .thenThrow(new BusinessException(ReportErrorCode.DISPUTE_NOT_A_PARTY));

        mockMvc.perform(post(DISPUTE_OPEN_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NOT_AS_DESCRIBED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_A_PARTY"));
    }

    @Test
    @DisplayName("Giao dịch chưa hoàn thành trả về 409 Conflict")
    void openDispute_notCompleted_returns409() throws Exception {
        String token = jwtService.issue(USER_ID, false);

        when(reportService.openDispute(eq(USER_ID), eq(100L), any(CreateDisputeRequest.class)))
                .thenThrow(new BusinessException(ReportErrorCode.DISPUTE_REQUEST_NOT_COMPLETED));

        mockMvc.perform(post(DISPUTE_OPEN_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NOT_AS_DESCRIBED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_REQUEST_NOT_COMPLETED"));
    }

    @Test
    @DisplayName("Quá 7 ngày kể từ khi Completed trả về 422 Unprocessable Entity")
    void openDispute_windowExpired_returns422() throws Exception {
        String token = jwtService.issue(USER_ID, false);

        when(reportService.openDispute(eq(USER_ID), eq(100L), any(CreateDisputeRequest.class)))
                .thenThrow(new BusinessException(ReportErrorCode.DISPUTE_WINDOW_EXPIRED));

        mockMvc.perform(post(DISPUTE_OPEN_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NOT_AS_DESCRIBED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DISPUTE_WINDOW_EXPIRED"));
    }

    @Test
    @DisplayName("Bên bị khiếu nại gửi giải trình hợp lệ trả về 200 OK")
    void respondDispute_valid_returns200() throws Exception {
        String token = jwtService.issue(USER_ID, false);

        doNothing().when(reportService).respondDispute(eq(USER_ID), eq(1L), any(DisputeResponseRequest.class));

        mockMvc.perform(post(DISPUTE_RESPONSE_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "note": "Áo đã kiểm tra cẩn thận trước khi bàn giao",
                          "evidenceMediaIds": [12]
                        }
                        """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Không phải bên bị khiếu nại gửi giải trình trả về 403 Forbidden")
    void respondDispute_notRespondent_returns403() throws Exception {
        String token = jwtService.issue(USER_ID, false);

        doThrow(new BusinessException(ReportErrorCode.DISPUTE_NOT_RESPONDENT))
                .when(reportService)
                .respondDispute(eq(USER_ID), eq(1L), any(DisputeResponseRequest.class));

        mockMvc.perform(post(DISPUTE_RESPONSE_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Giai trinh\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_RESPONDENT"));
    }

    @Test
    @DisplayName("Giải trình sau 3 ngày hết hạn trả về 422 Unprocessable Entity")
    void respondDispute_windowExpired_returns422() throws Exception {
        String token = jwtService.issue(USER_ID, false);

        doThrow(new BusinessException(ReportErrorCode.DISPUTE_RESPONSE_WINDOW_EXPIRED))
                .when(reportService)
                .respondDispute(eq(USER_ID), eq(1L), any(DisputeResponseRequest.class));

        mockMvc.perform(post(DISPUTE_RESPONSE_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Giai trinh muon\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DISPUTE_RESPONSE_WINDOW_EXPIRED"));
    }

    @Test
    @DisplayName("Khiếu nại không tìm thấy trả về 404 Not Found")
    void respondDispute_notFound_returns404() throws Exception {
        String token = jwtService.issue(USER_ID, false);

        doThrow(new BusinessException(ReportErrorCode.DISPUTE_NOT_FOUND))
                .when(reportService)
                .respondDispute(eq(USER_ID), eq(1L), any(DisputeResponseRequest.class));

        mockMvc.perform(post(DISPUTE_RESPONSE_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Giai trinh\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_FOUND"));
    }
}
