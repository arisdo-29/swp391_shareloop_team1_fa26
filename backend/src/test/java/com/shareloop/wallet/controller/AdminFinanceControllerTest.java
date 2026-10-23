package com.shareloop.wallet.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.wallet.FinanceErrorCode;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.CreditTypeTotal;
import com.shareloop.wallet.dto.FinanceSummaryResponse;
import com.shareloop.wallet.dto.TopUpReconciliation;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.mapper.FinanceReportCsvWriter;
import com.shareloop.wallet.service.AdminFinanceService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminFinanceController.class)
@Import({
    SecurityConfig.class,
    JwtConfig.class,
    JwtService.class,
    ClockConfig.class,
    WebConfig.class,
    FinanceReportCsvWriter.class
})
@ActiveProfiles("test")
class AdminFinanceControllerTest {

    private static final String REPORT_URL = "/api/v1/admin/finance/report";
    private static final String REPORT_CSV_URL = "/api/v1/admin/finance/report.csv";
    private static final String HISTORY_URL = "/api/v1/admin/finance/users/{userId}/history";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    AdminFinanceService adminFinanceService;

    @Test
    @DisplayName("GET /admin/finance/report: chưa đăng nhập trả về 401")
    void report_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(REPORT_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(adminFinanceService);
    }

    @Test
    @DisplayName("GET /admin/finance/report: user không phải Admin trả về 403")
    void report_nonAdmin_returns403() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(get(REPORT_URL).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(adminFinanceService);
    }

    @Test
    @DisplayName("GET /admin/finance/report: Admin gọi thành công trả về 200 và dữ liệu báo cáo")
    void report_admin_returns200() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        TopUpReconciliation reconciliation = new TopUpReconciliation(100L, 100L, 0L, true);
        FinanceSummaryResponse summary = new FinanceSummaryResponse(
                Instant.parse("2026-09-30T17:00:00Z"),
                Instant.parse("2026-10-17T17:00:00Z"),
                3L,
                100000L,
                107L,
                List.of(new CreditTypeTotal(CreditTxType.TOP_UP, 100L, 3L)),
                22L,
                List.of(new CreditTypeTotal(CreditTxType.POST_FEE, 10L, 2L)),
                reconciliation);

        when(adminFinanceService.getReport(any(), any())).thenReturn(summary);

        mockMvc.perform(get(REPORT_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashOrderCount").value(3))
                .andExpect(jsonPath("$.cashAmountVnd").value(100000))
                .andExpect(jsonPath("$.creditInTotal").value(107))
                .andExpect(jsonPath("$.creditOutTotal").value(22))
                .andExpect(jsonPath("$.reconciliation.matched").value(true))
                .andExpect(jsonPath("$.reconciliation.cashCredits").value(100))
                .andExpect(jsonPath("$.reconciliation.topUpCredits").value(100));

        verify(adminFinanceService).getReport(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 17));
    }

    @Test
    @DisplayName("GET /admin/finance/report: from sau to trả về 400 Bad Request")
    void report_fromAfterTo_returns400() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        when(adminFinanceService.getReport(any(), any()))
                .thenThrow(new BusinessException(FinanceErrorCode.INVALID_DATE_RANGE));

        mockMvc.perform(get(REPORT_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", "2026-10-20")
                        .param("to", "2026-10-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("GET /admin/finance/report.csv: chưa đăng nhập trả về 401")
    void reportCsv_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(REPORT_CSV_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(adminFinanceService);
    }

    @Test
    @DisplayName("GET /admin/finance/report.csv: USER không phải Admin trả về 403")
    void reportCsv_nonAdmin_returns403() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(get(REPORT_CSV_URL).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(adminFinanceService);
    }

    @Test
    @DisplayName("GET /admin/finance/report.csv: 3 byte đầu là BOM UTF-8 (EF BB BF)")
    void reportCsv_admin_startsWithUtf8Bom() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        when(adminFinanceService.getReport(any(), any())).thenReturn(sampleSummary());

        byte[] body = mockMvc.perform(get(REPORT_CSV_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-17"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(Arrays.copyOf(body, 3)).containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    @DisplayName("GET /admin/finance/report.csv: Admin tải file CSV đúng header và dòng dữ liệu")
    void reportCsv_admin_returnsCsvFile() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        when(adminFinanceService.getReport(any(), any())).thenReturn(sampleSummary());

        byte[] body = mockMvc.perform(get(REPORT_CSV_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-17"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"finance-report_2026-10-01_2026-10-17.csv\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿");
        assertThat(csv.substring(1).split("\r\n", -1))
                .containsExactly(
                        "Báo cáo tài chính ShareLoop",
                        "Từ ngày,2026-10-01",
                        "Đến ngày,2026-10-17",
                        "",
                        "Tiền mặt thu",
                        "cashOrderCount,cashAmountVnd",
                        "3,100000",
                        "",
                        "Credit cộng vào",
                        "type,amount,entryCount",
                        "ADMIN_ADJUST,7,1",
                        "TOP_UP,100,3",
                        "Tổng,107,4",
                        "",
                        "Credit trừ ra",
                        "type,amount,entryCount",
                        "AI_SEARCH_FEE,2,2",
                        "EDIT_FEE,5,1",
                        "POST_FEE,10,2",
                        "RENEW_FEE,5,1",
                        "Tổng,22,6",
                        "",
                        "Đối chiếu TopUp",
                        "cashCredits,topUpCredits,difference,matched",
                        "100,100,0,true",
                        "");

        verify(adminFinanceService).getReport(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 17));
    }

    @Test
    @DisplayName("GET /admin/finance/report.csv: from sau to trả về 400 INVALID_DATE_RANGE dạng JSON")
    void reportCsv_fromAfterTo_returns400() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        when(adminFinanceService.getReport(any(), any()))
                .thenThrow(new BusinessException(FinanceErrorCode.INVALID_DATE_RANGE));

        mockMvc.perform(get(REPORT_CSV_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", "2026-10-20")
                        .param("to", "2026-10-10"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("GET /admin/finance/users/{userId}/history: chưa đăng nhập trả về 401")
    void userHistory_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(HISTORY_URL, 42L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /admin/finance/users/{userId}/history: không phải Admin trả về 403")
    void userHistory_nonAdmin_returns403() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(get(HISTORY_URL, 42L).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /admin/finance/users/{userId}/history: Admin gọi thành công trả về 200")
    void userHistory_admin_returns200() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        WalletTransactionResponse txn = new WalletTransactionResponse(
                1L, CreditTxType.TOP_UP, 50, 50L, null, 1L, null, Instant.parse("2026-10-14T03:15:00Z"));
        PageResponse<WalletTransactionResponse> pageResponse = new PageResponse<>(List.of(txn), 0, 20, 1L);

        when(adminFinanceService.getUserHistory(eq(42L), any(), any(), eq(0), eq(20)))
                .thenReturn(pageResponse);

        mockMvc.perform(get(HISTORY_URL, 42L)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].amount").value(50))
                .andExpect(jsonPath("$.items[0].type").value("TOP_UP"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(adminFinanceService).getUserHistory(eq(42L), any(), any(), eq(0), eq(20));
    }

    @Test
    @DisplayName("GET /admin/finance/users/{userId}/history: userId không tồn tại trả về 404")
    void userHistory_userNotFound_returns404() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        when(adminFinanceService.getUserHistory(eq(999L), any(), any(), anyInt(), anyInt()))
                .thenThrow(new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        mockMvc.perform(get(HISTORY_URL, 999L).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    private static FinanceSummaryResponse sampleSummary() {
        return new FinanceSummaryResponse(
                Instant.parse("2026-09-30T17:00:00Z"),
                Instant.parse("2026-10-17T17:00:00Z"),
                3L,
                100000L,
                107L,
                List.of(
                        new CreditTypeTotal(CreditTxType.ADMIN_ADJUST, 7L, 1L),
                        new CreditTypeTotal(CreditTxType.TOP_UP, 100L, 3L)),
                22L,
                List.of(
                        new CreditTypeTotal(CreditTxType.AI_SEARCH_FEE, 2L, 2L),
                        new CreditTypeTotal(CreditTxType.EDIT_FEE, 5L, 1L),
                        new CreditTypeTotal(CreditTxType.POST_FEE, 10L, 2L),
                        new CreditTypeTotal(CreditTxType.RENEW_FEE, 5L, 1L)),
                new TopUpReconciliation(100L, 100L, 0L, true));
    }
}
