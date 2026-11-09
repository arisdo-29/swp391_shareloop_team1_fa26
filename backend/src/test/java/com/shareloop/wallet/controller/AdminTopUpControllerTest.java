package com.shareloop.wallet.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.wallet.dto.TopUpOrderDetailResponse;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.service.PaymentService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminTopUpController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminTopUpControllerTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @MockitoBean
    PaymentService paymentService;

    private static final String ADMIN = "/api/v1/admin/finance/topup-orders";

    @Test
    void anonymousCannotAccessAdminOrders() throws Exception {
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        verifyNoInteractions(paymentService);
    }

    @Test
    void regularUserCannotListAdminOrders() throws Exception {
        String token = "Bearer " + jwt.issue(42L, false);
        mvc.perform(get(ADMIN).header("Authorization", token)).andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test
    void adminCanListOrders() throws Exception {
        String token = "Bearer " + jwt.issue(1L, true);
        var order = new TopUpOrderDetailResponse(
                "ORD123",
                42L,
                100000L,
                100,
                1000,
                PaymentOrderStatus.SUCCESS,
                PaymentProvider.VNPAY,
                Instant.now(),
                Instant.now().plusSeconds(900),
                null,
                null,
                null,
                null,
                "Nguyen Van A",
                "a@fpt.edu.vn",
                null);
        when(paymentService.listOrders(any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(order), 0, 20, 1L));

        mvc.perform(get(ADMIN).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].orderCode").value("ORD123"))
                .andExpect(jsonPath("$.items[0].provider").value("VNPAY"))
                .andExpect(jsonPath("$.items[0].credits").value(100));

        verify(paymentService).listOrders(isNull(), isNull(), eq(false), eq(0), eq(20));
    }
}
