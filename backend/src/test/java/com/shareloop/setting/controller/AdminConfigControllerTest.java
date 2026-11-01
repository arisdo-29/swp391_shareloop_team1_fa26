package com.shareloop.setting.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.dto.ConfigResponse;
import com.shareloop.setting.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminConfigController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminConfigControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    ConfigService configService;

    private static ConfigResponse postFeeResponse() {
        return new ConfigResponse(
                "post_fee",
                "10",
                "5",
                "Phí đăng bài",
                "Số Credit được giữ khi đăng bài.",
                "FEES",
                "Phí bài đăng và nạp Credit");
    }

    @Test
    void adminCanListConfigsWithLabelDescriptionAndGroup() throws Exception {
        when(configService.listConfigs()).thenReturn(java.util.List.of(postFeeResponse()));
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(get("/api/v1/admin/configs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("post_fee"))
                .andExpect(jsonPath("$[0].label").value("Phí đăng bài"))
                .andExpect(jsonPath("$[0].description").value("Số Credit được giữ khi đăng bài."))
                .andExpect(jsonPath("$[0].group").value("FEES"))
                .andExpect(jsonPath("$[0].groupLabel").value("Phí bài đăng và nạp Credit"));
    }

    @Test
    void adminCanUpdateConfigUsingBodyKeyAndValue() throws Exception {
        when(configService.update(ConfigKey.POST_FEE, "10", null, 1L)).thenReturn(postFeeResponse());
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(put("/api/v1/admin/configs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"post_fee\",\"value\":\"10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("post_fee"))
                .andExpect(jsonPath("$.value").value("10"));

        verify(configService).update(ConfigKey.POST_FEE, "10", null, 1L);
    }

    @Test
    void nonAdminCannotUpdateConfig() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(put("/api/v1/admin/configs")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"post_fee\",\"value\":\"10\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(configService);
    }

    @Test
    void unknownConfigKeyReturnsNotFound() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(put("/api/v1/admin/configs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"unknown_key\",\"value\":\"1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(SettingErrorCode.SETTING_CONFIG_NOT_FOUND.code()));
    }

    @Test
    void legacyPathCanStillUpdateConfig() throws Exception {
        when(configService.update(ConfigKey.POST_FEE, "10", "raise fee", 1L)).thenReturn(postFeeResponse());
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(put("/api/v1/admin/configs/post_fee")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"10\",\"reason\":\"raise fee\"}"))
                .andExpect(status().isOk());

        verify(configService).update(ConfigKey.POST_FEE, "10", "raise fee", 1L);
    }
}
