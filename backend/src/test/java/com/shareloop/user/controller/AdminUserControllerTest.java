package com.shareloop.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.user.UserErrorCode;
import com.shareloop.user.service.AdminUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminUserControllerTest {

    private static final String LOCK_URL = "/api/v1/admin/users/{userId}/lock";
    private static final String UNLOCK_URL = "/api/v1/admin/users/{userId}/unlock";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    AdminUserService adminUserService;

    @Test
    @DisplayName("POST /lock: Chưa đăng nhập trả về 401")
    void lock_withoutToken_returns401() throws Exception {
        mockMvc.perform(post(LOCK_URL, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Lý do khoá\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(adminUserService);
    }

    @Test
    @DisplayName("POST /lock: Người dùng không phải Admin trả về 403")
    void lock_nonAdmin_returns403() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(post(LOCK_URL, 10L)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Lý do khoá\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(adminUserService);
    }

    @Test
    @DisplayName("POST /lock: Lý do để trống trả về 400 Validation Failed")
    void lock_blankReason_returns400() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(post(LOCK_URL, 10L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(adminUserService);
    }

    @Test
    @DisplayName("POST /lock: Tự khoá chính mình trả về 400 USER_CANNOT_LOCK_SELF")
    void lock_self_returns400() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        doThrow(new BusinessException(UserErrorCode.USER_CANNOT_LOCK_SELF))
                .when(adminUserService)
                .lock(eq(1L), eq(1L), any());

        mockMvc.perform(post(LOCK_URL, 1L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Tự khoá\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_CANNOT_LOCK_SELF"));
    }

    @Test
    @DisplayName("POST /lock: User không tồn tại trả về 404 USER_NOT_FOUND")
    void lock_userNotFound_returns404() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        doThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND))
                .when(adminUserService)
                .lock(eq(1L), eq(999L), any());

        mockMvc.perform(post(LOCK_URL, 999L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Khoá người không tồn tại\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /lock: Admin khoá tài khoản hợp lệ trả về 200 OK")
    void lock_validAdmin_returns200() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        doNothing().when(adminUserService).lock(1L, 10L, "Vi phạm quy định");

        mockMvc.perform(post(LOCK_URL, 10L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Vi phạm quy định\"}"))
                .andExpect(status().isOk());

        verify(adminUserService).lock(1L, 10L, "Vi phạm quy định");
    }

    @Test
    @DisplayName("POST /unlock: Chưa đăng nhập trả về 401")
    void unlock_withoutToken_returns401() throws Exception {
        mockMvc.perform(post(UNLOCK_URL, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Lý do mở khoá\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /unlock: Người dùng không phải Admin trả về 403")
    void unlock_nonAdmin_returns403() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(post(UNLOCK_URL, 10L)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Lý do mở khoá\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /unlock: Lý do để trống trả về 400 Validation Failed")
    void unlock_blankReason_returns400() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(post(UNLOCK_URL, 10L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /unlock: User không tồn tại trả về 404 USER_NOT_FOUND")
    void unlock_userNotFound_returns404() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        doThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND))
                .when(adminUserService)
                .unlock(eq(1L), eq(999L), any());

        mockMvc.perform(post(UNLOCK_URL, 999L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Mở khoá người không tồn tại\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /unlock: Admin mở khoá tài khoản hợp lệ trả về 200 OK")
    void unlock_validAdmin_returns200() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        doNothing().when(adminUserService).unlock(1L, 10L, "Đã hoàn tất giải trình");

        mockMvc.perform(post(UNLOCK_URL, 10L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Đã hoàn tất giải trình\"}"))
                .andExpect(status().isOk());

        verify(adminUserService).unlock(1L, 10L, "Đã hoàn tất giải trình");
    }
}
