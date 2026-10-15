package com.shareloop.auth.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.AuthErrorCode;
import com.shareloop.auth.dto.ChangePasswordRequest;
import com.shareloop.auth.service.AuthService;
import com.shareloop.auth.service.JwtService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Chỉ kiểm POST /change-password; các endpoint auth khác đã có AuthApiIT, AuthRegisterIT. */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AuthControllerTest {

    private static final String URL = "/api/v1/auth/change-password";
    private static final long USER_ID = 10L;
    private static final String VALID_BODY = "{\"currentPassword\":\"MatKhau@123\",\"newPassword\":\"MatKhauMoi@123\"}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    AuthService authService;

    @Test
    void changePassword_withoutToken_returns401() throws Exception {
        // given: /api/v1/auth/** là công khai nhưng endpoint này vẫn phải có token

        // when, then
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(authService);
    }

    @Test
    void changePassword_whenNewPasswordTooShort_returns400() throws Exception {
        // given
        String body = "{\"currentPassword\":\"MatKhau@123\",\"newPassword\":\"ngan\"}";

        // when, then
        mockMvc.perform(post(URL)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("newPassword"));
        verifyNoInteractions(authService);
    }

    @Test
    void changePassword_whenCurrentPasswordWrong_returns400CurrentPasswordIncorrect() throws Exception {
        // given
        doThrow(new BusinessException(AuthErrorCode.AUTH_CURRENT_PASSWORD_INCORRECT, null, "currentPassword", null))
                .when(authService)
                .changePassword(USER_ID, new ChangePasswordRequest("MatKhau@123", "MatKhauMoi@123"));

        // when, then
        mockMvc.perform(post(URL)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_CURRENT_PASSWORD_INCORRECT"))
                .andExpect(jsonPath("$.field").value("currentPassword"));
    }

    @Test
    void changePassword_withValidBody_returns204() throws Exception {
        // when, then
        mockMvc.perform(post(URL)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNoContent());
        verify(authService).changePassword(USER_ID, new ChangePasswordRequest("MatKhau@123", "MatKhauMoi@123"));
    }

    private String bearerToken() {
        return "Bearer " + jwtService.issue(USER_ID, false);
    }
}
