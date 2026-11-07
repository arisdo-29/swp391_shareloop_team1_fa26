package com.shareloop.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.media.service.MediaService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.CreateReportRequest;
import com.shareloop.report.dto.MyReportSummary;
import com.shareloop.report.dto.ReportResponse;
import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStage;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.service.MyReportQueryService;
import com.shareloop.report.service.ReportEvidenceService;
import com.shareloop.report.service.ReportService;
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

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class ReportControllerTest {

    private static final String URL = "/api/v1/reports";
    private static final long USER_ID = 10L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    ReportService reportService;

    @MockitoBean
    MyReportQueryService myReportQueryService;

    @MockitoBean
    ReportEvidenceService reportEvidenceService;

    @Test
    @DisplayName("Gửi báo cáo khi chưa đăng nhập trả về 401 Unauthorized")
    void createReport_withoutToken_returns401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Gửi body không hợp lệ (thiếu targetType, reason, description) trả về 400 Bad Request")
    void createReport_invalidBody_returns400() throws Exception {
        String token = jwtService.issue(USER_ID, false);

        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Gửi báo cáo hợp lệ trả về 201 Created và dữ liệu phản hồi")
    void createReport_valid_returns201() throws Exception {
        String token = jwtService.issue(USER_ID, false);

        ReportResponse response = new ReportResponse(
                1L,
                USER_ID,
                TargetType.ITEM,
                105L,
                null,
                null,
                ReportReason.NOT_AS_DESCRIBED,
                "Không đúng mô tả",
                ReportStage.BEFORE,
                ReportStatus.OPEN,
                Instant.parse("2026-10-17T16:00:00Z"));

        when(reportService.createReport(eq(USER_ID), any(CreateReportRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "targetType": "ITEM",
                          "itemId": 105,
                          "reason": "NOT_AS_DESCRIBED",
                          "description": "Không đúng mô tả"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.reporterId").value(USER_ID))
                .andExpect(jsonPath("$.targetType").value("ITEM"))
                .andExpect(jsonPath("$.itemId").value(105))
                .andExpect(jsonPath("$.reason").value("NOT_AS_DESCRIBED"))
                .andExpect(jsonPath("$.description").value("Không đúng mô tả"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("Xem hồ sơ đã gửi khi chưa đăng nhập trả về 401 Unauthorized")
    void listMine_withoutToken_returns401() throws Exception {
        // when, then
        mockMvc.perform(get(URL + "/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Xem hồ sơ cần giải trình trả về danh sách của người đang đăng nhập")
    void listAgainstMe_withToken_returnsMyReports() throws Exception {
        // given
        String token = jwtService.issue(USER_ID, false);
        Instant dueAt = Instant.parse("2026-11-09T03:00:00Z");
        MyReportSummary summary = new MyReportSummary(
                4L,
                TargetType.REQUEST,
                null,
                7L,
                ReportReason.NO_SHOW,
                ReportStatus.AWAITING_RESPONSE,
                "Giáo trình Toán rời rạc",
                Instant.parse("2026-11-06T03:00:00Z"),
                dueAt,
                null,
                true,
                null,
                null);
        when(myReportQueryService.listAgainstMe(USER_ID)).thenReturn(List.of(summary));

        // when, then
        mockMvc.perform(get(URL + "/against-me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4))
                .andExpect(jsonPath("$[0].requestId").value(7))
                .andExpect(jsonPath("$[0].responseWindowOpen").value(true))
                .andExpect(jsonPath("$[0].reporterId").doesNotExist());
    }

    @Test
    @DisplayName("Xem chi tiết hồ sơ không phải của mình trả về 404 REPORT_NOT_FOUND")
    void respondentView_notRespondent_returns404() throws Exception {
        // given
        String token = jwtService.issue(USER_ID, false);
        when(myReportQueryService.detailForRespondent(USER_ID, 7L))
                .thenThrow(new BusinessException(ReportErrorCode.REPORT_NOT_FOUND));

        // when, then
        mockMvc.perform(get(URL + "/7/respondent-view").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    @DisplayName("Bên bị phản ánh xem ảnh của hồ sơ nhận nội dung ảnh và không cho lưu bộ nhớ đệm")
    void respondentEvidence_respondent_returnsImageWithNoStore() throws Exception {
        // given
        String token = jwtService.issue(USER_ID, false);
        byte[] bytes = {1, 2, 3};
        when(reportEvidenceService.readForRespondent(USER_ID, 7L, 12L))
                .thenReturn(new MediaService.MediaContent(bytes, "image/jpeg"));

        // when, then
        mockMvc.perform(get(URL + "/7/evidence/12").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(bytes));
    }
}
