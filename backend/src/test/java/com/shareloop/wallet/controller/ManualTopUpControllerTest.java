package com.shareloop.wallet.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.wallet.service.ManualTopUpService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ManualTopUpController.class, AdminTopUpController.class})
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class ManualTopUpControllerTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @MockitoBean
    ManualTopUpService service;

    private static final String ADMIN = "/api/v1/admin/finance/topup-orders";

    @Test
    void anonymousCannotAccessOrders() throws Exception {
        mvc.perform(get("/api/v1/wallet/topup-orders/code")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/wallet/topup-orders/code/mark-paid")).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void regularUserCannotListApproveOrReject() throws Exception {
        String token = "Bearer " + jwt.issue(42L, false);
        mvc.perform(get(ADMIN).header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(post(ADMIN + "/code/approve")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(ADMIN + "/code/reject")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"no\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void usesTokenOwnerAndAdminIdentity() throws Exception {
        String token = "Bearer " + jwt.issue(42L, false);
        mvc.perform(get("/api/v1/wallet/topup-orders/code").header("Authorization", token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/wallet/topup-orders/code/mark-paid").header("Authorization", token))
                .andExpect(status().isOk());
        verify(service).getOrder(42L, "code");
        verify(service).markPaid(42L, "code");
        String admin = "Bearer " + jwt.issue(1L, true);
        when(service.list(any(), anyBoolean(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0));
        mvc.perform(get(ADMIN).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(post(ADMIN + "/code/approve")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        verify(service).approve(1L, "code", null);
        mvc.perform(post(ADMIN + "/code/reject")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"no\"}"))
                .andExpect(status().isOk());
        verify(service).reject(1L, "code", "no");
    }

    @Test
    void adminList_withoutParameters_asksForEveryStatusAndUnsubmittedOrders() throws Exception {
        // given
        String admin = "Bearer " + jwt.issue(1L, true);
        when(service.list(any(), anyBoolean(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0));

        // when
        mvc.perform(get(ADMIN).header("Authorization", admin)).andExpect(status().isOk());

        // then: status null là mọi trạng thái, submittedOnly mặc định false
        verify(service).list(isNull(), eq(false), eq(0), eq(20));
    }

    @Test
    void validatesPaginationAndReviewBody() throws Exception {
        String admin = "Bearer " + jwt.issue(1L, true);
        mvc.perform(get(ADMIN).param("size", "101").header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(post(ADMIN + "/code/reject")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(ADMIN + "/code/approve")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
