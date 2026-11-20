package com.shareloop.wallet.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.shareloop.wallet.dto.TopUpOrderDetailResponse;
import com.shareloop.wallet.dto.TopUpOrderResponse;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.service.PaymentService;
import java.time.Instant;
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
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-17T08:15:00Z");

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
                        "/wallet/topup/mock-checkout?orderCode=abc123",
                        EXPIRES_AT));

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
                .andExpect(jsonPath("$.redirectUrl").value("/wallet/topup/mock-checkout?orderCode=abc123"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-17T08:15:00Z"));
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

    @Test
    void getOrder_whenOwnOrder_returnsStatusAndExpiresAt() throws Exception {
        // given (B29): FE đếm ngược theo expiresAt do backend trả, không tự tính
        String token = jwtService.issue(10L, false);
        when(paymentService.getOwnOrder(10L, "abc123"))
                .thenReturn(new TopUpOrderDetailResponse(
                        "abc123",
                        10L,
                        20_000,
                        20,
                        1_000,
                        PaymentOrderStatus.PENDING,
                        PaymentProvider.VNPAY,
                        Instant.parse("2026-11-17T08:00:00Z"),
                        EXPIRES_AT,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

        // when + then
        mockMvc.perform(get(URL + "/abc123").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCode").value("abc123"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-17T08:15:00Z"));
    }

    @Test
    void getOrder_whenOrderOfOtherUserOrMissing_returns404() throws Exception {
        // given
        String token = jwtService.issue(10L, false);
        when(paymentService.getOwnOrder(10L, "other"))
                .thenThrow(new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND));

        // when + then
        mockMvc.perform(get(URL + "/other").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_PAYMENT_ORDER_NOT_FOUND"));
    }

    @Test
    void getOrder_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(URL + "/abc123")).andExpect(status().isUnauthorized());
    }

    @Test
    void cancelOrder_returnsOrderStatusAfterCancelWithReason() throws Exception {
        // given: service hủy đơn của chính user 10 và trả trạng thái mới
        String token = jwtService.issue(10L, false);
        when(paymentService.cancelOwnOrder(10L, "abc123"))
                .thenReturn(new TopUpOrderDetailResponse(
                        "abc123",
                        10L,
                        20_000,
                        20,
                        1_000,
                        PaymentOrderStatus.FAILED,
                        PaymentProvider.VNPAY,
                        Instant.parse("2026-11-17T08:00:00Z"),
                        EXPIRES_AT,
                        null,
                        null,
                        null,
                        "Bạn đã hủy đơn nạp.",
                        null,
                        null,
                        null,
                        "Bạn đã hủy đơn nạp."));

        // when + then
        mockMvc.perform(post(URL + "/abc123/cancel").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Bạn đã hủy đơn nạp."));
    }

    @Test
    void cancelOrder_withoutToken_returns401() throws Exception {
        mockMvc.perform(post(URL + "/abc123/cancel")).andExpect(status().isUnauthorized());
    }
}
