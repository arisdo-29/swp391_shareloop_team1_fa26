package com.shareloop.setting.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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

    @Test
    void adminCanUpdateConfigUsingBodyKeyAndValue() throws Exception {
        when(configService.update(ConfigKey.CHAT_AI_THRESHOLD, "0.8", null, 1L))
                .thenReturn(new ConfigResponse("chat_ai_threshold", "0.8", "0.7"));
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(put("/api/v1/admin/configs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"chat_ai_threshold\",\"value\":\"0.8\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("chat_ai_threshold"))
                .andExpect(jsonPath("$.value").value("0.8"));

        verify(configService).update(ConfigKey.CHAT_AI_THRESHOLD, "0.8", null, 1L);
    }

    @Test
    void nonAdminCannotUpdateConfig() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(put("/api/v1/admin/configs")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"chat_ai_threshold\",\"value\":\"0.8\"}"))
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
        when(configService.update(ConfigKey.CHAT_AI_THRESHOLD, "0.8", "raise threshold", 1L))
                .thenReturn(new ConfigResponse("chat_ai_threshold", "0.8", "0.7"));
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(put("/api/v1/admin/configs/chat_ai_threshold")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"0.8\",\"reason\":\"raise threshold\"}"))
                .andExpect(status().isOk());

        verify(configService).update(ConfigKey.CHAT_AI_THRESHOLD, "0.8", "raise threshold", 1L);
    }
}
