package com.shareloop.wallet.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.service.CreditService;
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
}
