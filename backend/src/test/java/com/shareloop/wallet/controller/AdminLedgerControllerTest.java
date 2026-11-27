package com.shareloop.wallet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.wallet.dto.AdminLedgerPage;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.service.AdminLedgerService;
import java.time.LocalDate;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(AdminLedgerController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminLedgerControllerTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @MockitoBean
    AdminLedgerService adminLedgerService;

    private static final String LEDGER = "/api/v1/admin/finance/ledger";

    @Test
    void anonymousAndRegularUserCannotReadLedgerOrCsv() throws Exception {
        String userToken = "Bearer " + jwt.issue(42L, false);

        mvc.perform(get(LEDGER)).andExpect(status().isUnauthorized());
        mvc.perform(get(LEDGER).header("Authorization", userToken)).andExpect(status().isForbidden());
        mvc.perform(get(LEDGER + ".csv").header("Authorization", userToken)).andExpect(status().isForbidden());
        mvc.perform(get(LEDGER + ".xlsx").header("Authorization", userToken)).andExpect(status().isForbidden());
        verifyNoInteractions(adminLedgerService);
    }

    @Test
    void adminCanReadLedgerWithDefaultsAndTotals() throws Exception {
        String token = "Bearer " + jwt.issue(1L, true);
        when(adminLedgerService.list(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new AdminLedgerPage(List.of(), 0, 20, 0, 70L, -15L));

        mvc.perform(get(LEDGER).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIn").value(70))
                .andExpect(jsonPath("$.totalOut").value(-15));

        verify(adminLedgerService).list(isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20));
    }

    @Test
    void adminCanDownloadCsvWithFilters() throws Exception {
        String token = "Bearer " + jwt.issue(1L, true);
        when(adminLedgerService.exportCsv(any(), any(), any(), any(), any())).thenReturn(new byte[] {1});

        mvc.perform(get(LEDGER + ".csv").param("type", "TOP_UP").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"));

        verify(adminLedgerService).exportCsv(eq(CreditTxType.TOP_UP), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void adminCanExportExcelWithFilters() throws Exception {
        String token = "Bearer " + jwt.issue(1L, true);
        LocalDate from = LocalDate.parse("2026-11-01");
        LocalDate to = LocalDate.parse("2026-11-28");
        byte[] workbook = {1, 2, 3};
        when(adminLedgerService.exportExcel(CreditTxType.TOP_UP, 5L, "ORD", from, to))
                .thenReturn(workbook);

        mvc.perform(get(LEDGER + ".xlsx")
                        .param("type", "TOP_UP")
                        .param("userId", "5")
                        .param("q", "ORD")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(
                                "Content-Disposition", Matchers.containsString("lich-su-giao-dich-credit.xlsx")))
                .andExpect(MockMvcResultMatchers.content().bytes(workbook));
    }
}
