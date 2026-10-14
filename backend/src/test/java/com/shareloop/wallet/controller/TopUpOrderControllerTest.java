package com.shareloop.wallet.controller;

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
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.TopUpOrderResponse;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TopUpOrderController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class TopUpOrderControllerTest {

    private static final String URL = "/api/v1/wallet/topup-orders";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    PaymentService paymentService;

    @Test
    void createTopUpOrder_withoutToken_returns401() throws Exception {
        // given: request không có header Authorization

        // when + then
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"amountVnd\": 20000}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void createTopUpOrder_whenAmountMissing_returns400() throws Exception {
        // given
        String token = jwtService.issue(10L, false);

        // when + then
        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("amountVnd"));
    }

    @Test
    void createTopUpOrder_whenValid_returns201WithOrderOfTokenUser() throws Exception {
        // given: token của user 10, service trả đơn vừa tạo
        String token = jwtService.issue(10L, false);
        when(paymentService.createTopUpOrder(10L, 20_000L))
                .thenReturn(new TopUpOrderResponse(
                        "abc123",
                        20_000,
                        20,
                        PaymentOrderStatus.PENDING,
                        PaymentProvider.MOCK,
                        "/wallet/topup/mock-checkout?orderCode=abc123"));

        // when + then
        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountVnd\": 20000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderCode").value("abc123"))
                .andExpect(jsonPath("$.credits").value(20))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.provider").value("MOCK"))
                .andExpect(jsonPath("$.redirectUrl").value("/wallet/topup/mock-checkout?orderCode=abc123"));
    }

    @Test
    void createTopUpOrder_whenBelowMinimum_returns422() throws Exception {
        // given
        String token = jwtService.issue(10L, false);
        when(paymentService.createTopUpOrder(10L, 5_000L))
                .thenThrow(new BusinessException(WalletErrorCode.WALLET_TOPUP_AMOUNT_INVALID));

        // when + then
        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountVnd\": 5000}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WALLET_TOPUP_AMOUNT_INVALID"));
    }
}
