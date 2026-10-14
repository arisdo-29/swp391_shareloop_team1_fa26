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
import com.shareloop.wallet.dto.TopUpConfirmResponse;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Profile test bật app.payment.mock.enabled=true (application-test.yml) nên controller này được tạo. */
@WebMvcTest(MockTopUpController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class MockTopUpControllerTest {

    private static final String URL = "/api/v1/wallet/topup-orders/abc123/mock-confirm";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    PaymentService paymentService;

    @Test
    void confirmMockPayment_withoutToken_returns401() throws Exception {
        // given: request không có header Authorization

        // when + then
        mockMvc.perform(post(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void confirmMockPayment_withToken_returnsSuccessAndWallet() throws Exception {
        // given
        String token = jwtService.issue(10L, false);
        when(paymentService.confirmMockPayment(10L, "abc123"))
                .thenReturn(new TopUpConfirmResponse(
                        "abc123", PaymentOrderStatus.SUCCESS, 20, new WalletResponse(40, 5, 35, true)));

        // when + then
        mockMvc.perform(post(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.credits").value(20))
                .andExpect(jsonPath("$.wallet.creditBalance").value(40))
                .andExpect(jsonPath("$.wallet.hasToppedUp").value(true));
    }

    @Test
    void confirmMockPayment_whenOrderNotFound_returns404() throws Exception {
        // given
        String token = jwtService.issue(10L, false);
        when(paymentService.confirmMockPayment(10L, "abc123"))
                .thenThrow(new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND));

        // when + then
        mockMvc.perform(post(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_PAYMENT_ORDER_NOT_FOUND"));
    }
}
