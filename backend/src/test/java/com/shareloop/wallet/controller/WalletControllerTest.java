package com.shareloop.wallet.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.service.CreditService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WalletController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class WalletControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    CreditService creditService;

    @Test
    void getMyWallet_withoutToken_returns401() throws Exception {
        // given: request không có header Authorization

        // when + then
        mockMvc.perform(get("/api/v1/wallet"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void getMyWallet_withToken_returnsWalletOfTokenUser() throws Exception {
        // given: token của user 10, service trả ví tương ứng
        String token = jwtService.issue(10L, false);
        when(creditService.getWallet(10L)).thenReturn(new WalletResponse(20, 5, 15, true));

        // when + then
        mockMvc.perform(get("/api/v1/wallet").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditBalance").value(20))
                .andExpect(jsonPath("$.heldCredit").value(5))
                .andExpect(jsonPath("$.available").value(15))
                .andExpect(jsonPath("$.hasToppedUp").value(true));
    }

    @Test
    void getMyWallet_whenWalletMissing_returns404() throws Exception {
        // given
        String token = jwtService.issue(10L, false);
        when(creditService.getWallet(10L)).thenThrow(new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        // when + then
        mockMvc.perform(get("/api/v1/wallet").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    // ---------- GET /wallet/transactions ----------

    @Test
    void listMyTransactions_withoutToken_returns401() throws Exception {
        // given: request không có header Authorization

        // when + then
        mockMvc.perform(get("/api/v1/wallet/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void listMyTransactions_withToken_returnsPageOfTokenUser() throws Exception {
        // given: token của user 10, lọc theo from/to dạng ISO-8601 UTC
        String token = jwtService.issue(10L, false);
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-01T00:00:00Z");
        WalletTransactionResponse topUpRow = new WalletTransactionResponse(
                12L, CreditTxType.TOP_UP, 20, 40, null, 7L, null, Instant.parse("2026-10-14T03:15:00Z"));
        when(creditService.getTransactions(10L, from, to, 0, 20))
                .thenReturn(new PageResponse<>(List.of(topUpRow), 0, 20, 1));

        // when + then
        mockMvc.perform(get("/api/v1/wallet/transactions")
                        .param("from", "2026-10-01T00:00:00Z")
                        .param("to", "2026-11-01T00:00:00Z")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("TOP_UP"))
                .andExpect(jsonPath("$.items[0].amount").value(20))
                .andExpect(jsonPath("$.items[0].balanceAfter").value(40))
                .andExpect(jsonPath("$.items[0].paymentOrderId").value(7))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-10-14T03:15:00Z"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listMyTransactions_whenSizeTooLarge_returns400() throws Exception {
        // given
        String token = jwtService.issue(10L, false);

        // when + then
        mockMvc.perform(get("/api/v1/wallet/transactions")
                        .param("size", "101")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void listMyTransactions_whenFromNotBeforeTo_returns422() throws Exception {
        // given
        String token = jwtService.issue(10L, false);
        Instant moment = Instant.parse("2026-10-01T00:00:00Z");
        when(creditService.getTransactions(10L, moment, moment, 0, 20))
                .thenThrow(new BusinessException(WalletErrorCode.WALLET_INVALID_DATE_RANGE));

        // when + then
        mockMvc.perform(get("/api/v1/wallet/transactions")
                        .param("from", "2026-10-01T00:00:00Z")
                        .param("to", "2026-10-01T00:00:00Z")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WALLET_INVALID_DATE_RANGE"));
    }
}
