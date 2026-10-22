package com.shareloop.reputation.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.shareloop.reputation.ReputationErrorCode;
import com.shareloop.reputation.dto.GraceStarResponse;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.user.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminReputationController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminReputationControllerTest {

    private static final String GRACE_STAR_URL = "/api/v1/admin/users/{userId}/grace-star";
    private static final String BODY = "{\"reason\":\"Đã giải trình và cam kết không tái phạm\"}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    ReputationService reputationService;

    @Test
    @DisplayName("POST /grace-star: chưa đăng nhập trả về 401")
    void grantGraceStar_withoutToken_returns401() throws Exception {
        mockMvc.perform(post(GRACE_STAR_URL, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(reputationService);
    }

    @Test
    @DisplayName("POST /grace-star: USER không phải Admin trả về 403")
    void grantGraceStar_nonAdmin_returns403() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(post(GRACE_STAR_URL, 7L)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(reputationService);
    }

    @Test
    @DisplayName("POST /grace-star: thiếu lý do trả về 400 VALIDATION_FAILED")
    void grantGraceStar_blankReason_returns400() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(post(GRACE_STAR_URL, 7L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("reason"));

        verifyNoInteractions(reputationService);
    }

    @Test
    @DisplayName("POST /grace-star: Admin cấp thành công trả về 200 và số sao mới")
    void grantGraceStar_admin_returns200() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        when(reputationService.grantGraceStar(1L, 7L, "Đã giải trình và cam kết không tái phạm"))
                .thenReturn(new GraceStarResponse(7L, 1, true));

        mockMvc.perform(post(GRACE_STAR_URL, 7L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.trustStars").value(1))
                .andExpect(jsonPath("$.graceStarUsed").value(true));

        verify(reputationService).grantGraceStar(eq(1L), eq(7L), eq("Đã giải trình và cam kết không tái phạm"));
    }

    @Test
    @DisplayName("POST /grace-star: cấp lần hai trả về 409 REPUTATION_GRACE_STAR_ALREADY_USED")
    void grantGraceStar_secondTime_returns409() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        when(reputationService.grantGraceStar(anyLong(), anyLong(), anyString()))
                .thenThrow(new BusinessException(ReputationErrorCode.REPUTATION_GRACE_STAR_ALREADY_USED));

        mockMvc.perform(post(GRACE_STAR_URL, 7L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPUTATION_GRACE_STAR_ALREADY_USED"));
    }

    @Test
    @DisplayName("POST /grace-star: người dùng còn sao trả về 409 REPUTATION_GRACE_STAR_NOT_ELIGIBLE")
    void grantGraceStar_userStillHasStars_returns409() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        when(reputationService.grantGraceStar(anyLong(), anyLong(), anyString()))
                .thenThrow(new BusinessException(ReputationErrorCode.REPUTATION_GRACE_STAR_NOT_ELIGIBLE));

        mockMvc.perform(post(GRACE_STAR_URL, 7L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPUTATION_GRACE_STAR_NOT_ELIGIBLE"));
    }

    @Test
    @DisplayName("POST /grace-star: không tìm thấy người dùng trả về 404 USER_NOT_FOUND")
    void grantGraceStar_userNotFound_returns404() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        when(reputationService.grantGraceStar(anyLong(), anyLong(), anyString()))
                .thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        mockMvc.perform(post(GRACE_STAR_URL, 999L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
