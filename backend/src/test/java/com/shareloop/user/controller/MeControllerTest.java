package com.shareloop.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.user.UserErrorCode;
import com.shareloop.user.dto.ChangePhoneOtpResponse;
import com.shareloop.user.dto.ChangePhoneRequest;
import com.shareloop.user.dto.MeResponse;
import com.shareloop.user.dto.SendChangePhoneOtpRequest;
import com.shareloop.user.dto.UpdateProfileRequest;
import com.shareloop.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class MeControllerTest {

    private static final String URL = "/api/v1/me";
    private static final long USER_ID = 10L;
    private static final MeResponse ME = new MeResponse(
            USER_ID, "an@example.com", "Nguyễn Văn An", "0901234567", false, 12L, 30L, "/api/v1/media/30");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    UserService userService;

    @Test
    void getMe_withoutToken_returns401() throws Exception {
        // given: request không có header Authorization

        // when, then
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void getMe_withToken_returnsProfile() throws Exception {
        // given
        when(userService.getMe(USER_ID)).thenReturn(ME);

        // when, then
        mockMvc.perform(get(URL).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("an@example.com"))
                .andExpect(jsonPath("$.phone").value("0901234567"))
                .andExpect(jsonPath("$.isAdmin").value(false))
                .andExpect(jsonPath("$.areaId").value(12))
                .andExpect(jsonPath("$.avatarUrl").value("/api/v1/media/30"));
    }

    @Test
    void updateMe_withoutToken_returns401() throws Exception {
        // given
        String body = "{\"fullName\":\"Nguyễn Văn An\",\"areaId\":12,\"avatarMediaId\":null}";

        // when, then
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(userService);
    }

    @Test
    void updateMe_whenFullNameBlank_returns400() throws Exception {
        // given
        String body = "{\"fullName\":\"   \",\"areaId\":12,\"avatarMediaId\":null}";

        // when, then
        mockMvc.perform(put(URL).header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("fullName"));
        verifyNoInteractions(userService);
    }

    @Test
    void updateMe_whenAreaInvalid_returns400AreaInvalid() throws Exception {
        // given
        when(userService.updateProfile(eq(USER_ID), any(UpdateProfileRequest.class)))
                .thenThrow(new BusinessException(UserErrorCode.USER_AREA_INVALID, null, "areaId", null));
        String body = "{\"fullName\":\"Nguyễn Văn An\",\"areaId\":999,\"avatarMediaId\":null}";

        // when, then
        mockMvc.perform(put(URL).header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_AREA_INVALID"))
                .andExpect(jsonPath("$.field").value("areaId"));
    }

    @Test
    void updateMe_withValidBody_returnsUpdatedProfile() throws Exception {
        // given: email gửi kèm bị bỏ qua vì UpdateProfileRequest không có trường này
        UpdateProfileRequest expected = new UpdateProfileRequest("Nguyễn Văn An", 12L, 30L);
        when(userService.updateProfile(USER_ID, expected)).thenReturn(ME);
        String body = "{\"fullName\":\"Nguyễn Văn An\",\"areaId\":12,\"avatarMediaId\":30,\"email\":\"x@y.z\"}";

        // when, then
        mockMvc.perform(put(URL).header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.email").value("an@example.com"))
                .andExpect(jsonPath("$.avatarMediaId").value(30));
    }

    @Test
    void sendChangePhoneOtp_withoutToken_returns401() throws Exception {
        String body = "{\"newPhone\":\"0987654321\"}";

        mockMvc.perform(post(URL + "/phone/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(userService);
    }

    @Test
    void sendChangePhoneOtp_whenInvalidPhoneRegex_returns400ValidationFailed() throws Exception {
        String body = "{\"newPhone\":\"12345\"}";

        mockMvc.perform(post(URL + "/phone/otp")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("newPhone"));
        verifyNoInteractions(userService);
    }

    @Test
    void sendChangePhoneOtp_whenValid_returns200() throws Exception {
        SendChangePhoneOtpRequest request = new SendChangePhoneOtpRequest("0987654321");
        when(userService.sendChangePhoneOtp(USER_ID, request))
                .thenReturn(new ChangePhoneOtpResponse("an@example.com", 300, 60));
        String body = "{\"newPhone\":\"0987654321\"}";

        mockMvc.perform(post(URL + "/phone/otp")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("an@example.com"))
                .andExpect(jsonPath("$.otpExpiresInSeconds").value(300))
                .andExpect(jsonPath("$.resendCooldownSeconds").value(60));
    }

    @Test
    void changePhone_withoutToken_returns401() throws Exception {
        String body = "{\"newPhone\":\"0987654321\",\"otp\":\"123456\"}";

        mockMvc.perform(put(URL + "/phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(userService);
    }

    @Test
    void changePhone_whenInvalidOtp_returns400ValidationFailed() throws Exception {
        String body = "{\"newPhone\":\"0987654321\",\"otp\":\"abc\"}";

        mockMvc.perform(put(URL + "/phone")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("otp"));
        verifyNoInteractions(userService);
    }

    @Test
    void changePhone_whenValid_returns200() throws Exception {
        ChangePhoneRequest request = new ChangePhoneRequest("0987654321", "123456");
        MeResponse updated = new MeResponse(
                USER_ID, "an@example.com", "Nguyễn Văn An", "0987654321", false, 12L, 30L, "/api/v1/media/30");
        when(userService.changePhone(USER_ID, request)).thenReturn(updated);
        String body = "{\"newPhone\":\"0987654321\",\"otp\":\"123456\"}";

        mockMvc.perform(put(URL + "/phone")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("0987654321"));
    }

    private String bearerToken() {
        return "Bearer " + jwtService.issue(USER_ID, false);
    }
}
