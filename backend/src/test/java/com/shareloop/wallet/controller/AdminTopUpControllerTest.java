package com.shareloop.wallet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.wallet.dto.AdminTopUpOrderDetail;
import com.shareloop.wallet.dto.AdminTopUpOrderResponse;
import com.shareloop.wallet.dto.ReconcileTopUpRequest;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.service.AdminTopUpService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
    AdminTopUpService adminTopUpService;

    private static final String ADMIN = "/api/v1/admin/finance/topup-orders";

    @Test
    void anonymousCannotAccessAdminOrders() throws Exception {
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        verifyNoInteractions(adminTopUpService);
    }

    @Test
    void regularUserCannotListAdminOrders() throws Exception {
        String token = "Bearer " + jwt.issue(42L, false);
        mvc.perform(get(ADMIN).header("Authorization", token)).andExpect(status().isForbidden());
        verifyNoInteractions(adminTopUpService);
    }

    @Test
    void regularUserCannotUseSummaryDetailExportOrReconcile() throws Exception {
        String token = "Bearer " + jwt.issue(42L, false);
        mvc.perform(get(ADMIN + "/summary").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN + "/export.csv").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN + "/ORD123").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(post(ADMIN + "/ORD123/reconcile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"GRANT_CREDIT\",\"note\":\"Đã đối chiếu\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminTopUpService);
    }

    @Test
    void adminCanListOrders() throws Exception {
        String token = "Bearer " + jwt.issue(1L, true);
        when(adminTopUpService.list(any(), any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(order()), 0, 20, 1L));

        mvc.perform(get(ADMIN).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].orderCode").value("ORD123"))
                .andExpect(jsonPath("$.items[0].provider").value("VNPAY"))
                .andExpect(jsonPath("$.items[0].needsReconcile").value(true))
                .andExpect(jsonPath("$.items[0].paymentUrl").doesNotExist());

        verify(adminTopUpService).list(isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), eq(0), eq(20));
    }

    @Test
    void reconcile_whenNoteTooShort_returns400() throws Exception {
        String token = "Bearer " + jwt.issue(1L, true);

        mvc.perform(post(ADMIN + "/ORD123/reconcile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"GRANT_CREDIT\",\"note\":\"ab\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adminTopUpService);
    }

    @Test
    void reconcile_whenValid_passesAdminIdAndRequest() throws Exception {
        String token = "Bearer " + jwt.issue(1L, true);
        when(adminTopUpService.reconcile(anyLong(), any(), any()))
                .thenReturn(new AdminTopUpOrderDetail(order(), Map.of(), List.of()));

        mvc.perform(post(ADMIN + "/ORD123/reconcile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REFUNDED_OUTSIDE\",\"note\":\"Đã hoàn qua ngân hàng\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.orderCode").value("ORD123"));

        verify(adminTopUpService)
                .reconcile(
                        eq(1L),
                        eq("ORD123"),
                        eq(new ReconcileTopUpRequest(
                                ReconcileTopUpRequest.Action.REFUNDED_OUTSIDE, "Đã hoàn qua ngân hàng")));
    }

    private static AdminTopUpOrderResponse order() {
        return new AdminTopUpOrderResponse(
                "ORD123",
                42L,
                "Nguyen Van A",
                "a@fpt.edu.vn",
                100000L,
                100,
                PaymentProvider.VNPAY,
                PaymentOrderStatus.EXPIRED,
                "123456",
                Instant.now(),
                Instant.now(),
                true,
                null,
                null,
                null);
    }
}
