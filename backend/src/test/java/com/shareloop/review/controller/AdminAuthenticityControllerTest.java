package com.shareloop.review.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.review.ReviewErrorCode;
import com.shareloop.review.service.AuthenticityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAuthenticityController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminAuthenticityControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    AuthenticityService authenticityService;

    @Test
    @DisplayName("grant: trả 401 khi chưa đăng nhập")
    void grant_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/authenticity/42/grant")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("grant: trả 403 khi người dùng không phải Admin")
    void grant_forbiddenForNonAdmin() throws Exception {
        String userToken = jwtService.issue(10L, false);

        mockMvc.perform(post("/api/v1/admin/authenticity/42/grant").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("grant: trả 204 khi Admin cấp nhãn thành công")
    void grant_success() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        doNothing().when(authenticityService).grant(eq(42L), eq(1L));

        mockMvc.perform(post("/api/v1/admin/authenticity/42/grant").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        verify(authenticityService).grant(42L, 1L);
    }

    @Test
    @DisplayName("grant: trả 409 khi trạng thái không hợp lệ (không phải PENDING)")
    void grant_invalidStatus_returns409() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        doThrow(new BusinessException(ReviewErrorCode.AUTHENTICITY_INVALID_STATUS))
                .when(authenticityService)
                .grant(eq(42L), eq(1L));

        mockMvc.perform(post("/api/v1/admin/authenticity/42/grant").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTHENTICITY_INVALID_STATUS"));
    }

    @Test
    @DisplayName("reject: trả 204 khi Admin từ chối nhãn thành công")
    void reject_success() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        doNothing().when(authenticityService).reject(eq(42L), eq("Hoá đơn không hợp lệ"), eq(1L));

        mockMvc.perform(post("/api/v1/admin/authenticity/42/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Hoá đơn không hợp lệ\"}"))
                .andExpect(status().isNoContent());

        verify(authenticityService).reject(42L, "Hoá đơn không hợp lệ", 1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\": \"\"}", "{\"reason\": \"   \"}"})
    @DisplayName("reject: trả 400 khi thiếu hoặc lý do rỗng")
    void reject_invalidReason_returns400(String body) throws Exception {
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(post("/api/v1/admin/authenticity/42/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(authenticityService, never()).reject(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("revoke: trả 204 khi Admin thu hồi nhãn thành công")
    void revoke_success() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        doNothing().when(authenticityService).revoke(eq(42L), eq("Chứng từ giả"), eq(1L));

        mockMvc.perform(post("/api/v1/admin/authenticity/42/revoke")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Chứng từ giả\"}"))
                .andExpect(status().isNoContent());

        verify(authenticityService).revoke(42L, "Chứng từ giả", 1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\": \"\"}", "{\"reason\": \"   \"}"})
    @DisplayName("revoke: trả 400 khi thiếu hoặc lý do rỗng")
    void revoke_invalidReason_returns400(String body) throws Exception {
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(post("/api/v1/admin/authenticity/42/revoke")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(authenticityService, never()).revoke(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("revoke: trả 409 khi bài chưa được cấp nhãn VERIFIED")
    void revoke_notVerified_returns409() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        doThrow(new BusinessException(ReviewErrorCode.AUTHENTICITY_NOT_VERIFIED))
                .when(authenticityService)
                .revoke(eq(42L), eq("Chứng từ giả"), eq(1L));

        mockMvc.perform(post("/api/v1/admin/authenticity/42/revoke")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Chứng từ giả\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTHENTICITY_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("reject: trả 404 khi bài không tồn tại")
    void reject_notFound_returns404() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        doThrow(new BusinessException(ReviewErrorCode.AUTHENTICITY_ITEM_NOT_FOUND))
                .when(authenticityService)
                .reject(eq(999L), eq("Hoá đơn không hợp lệ"), eq(1L));

        mockMvc.perform(post("/api/v1/admin/authenticity/999/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Hoá đơn không hợp lệ\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTHENTICITY_ITEM_NOT_FOUND"));
    }
}
