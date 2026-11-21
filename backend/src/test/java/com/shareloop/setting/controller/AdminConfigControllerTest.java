package com.shareloop.setting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.dto.BatchUpdateConfigResponse;
import com.shareloop.setting.dto.ConfigChangeResponse;
import com.shareloop.setting.dto.ConfigPreviewResponse;
import com.shareloop.setting.dto.ConfigResponse;
import com.shareloop.setting.service.ConfigBatchService;
import com.shareloop.setting.service.ConfigService;
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

    @MockitoBean
    ConfigBatchService configBatchService;

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
    void adminCanPreviewConfigChange() throws Exception {
        ConfigPreviewResponse preview = new ConfigPreviewResponse(
                List.of(
                        new ConfigChangeResponse("boost_days", "Số ngày mỗi gói đẩy bài", "3", "4", null, null, null),
                        new ConfigChangeResponse(
                                "boost_max_days",
                                "Số ngày đẩy tối đa",
                                "6",
                                "8",
                                "boost_days",
                                "Số ngày mỗi gói đẩy bài",
                                "Đồng bộ theo Số ngày mỗi gói đẩy bài")),
                List.of(),
                List.of());
        when(configBatchService.preview("boost_days", "4")).thenReturn(preview);
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(post("/api/v1/admin/configs/preview")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"boost_days\",\"value\":\"4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[1].key").value("boost_max_days"))
                .andExpect(jsonPath("$.changes[1].causedByKey").value("boost_days"))
                .andExpect(jsonPath("$.changes[1].newValue").value("8"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void batchWithEmptyChangesReturnsBadRequest() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(put("/api/v1/admin/configs/batch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changes\":[],\"reason\":\"Thử\",\"acknowledgedWarnings\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(configBatchService);
    }

    @Test
    void adminCanUpdateBatch() throws Exception {
        when(configBatchService.updateBatch(any(), eq("Đổi gói đẩy"), eq(true), eq(1L)))
                .thenReturn(new BatchUpdateConfigResponse(List.of(postFeeResponse())));
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(put("/api/v1/admin/configs/batch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changes\":[{\"key\":\"boost_days\",\"value\":\"4\"}],"
                                + "\"reason\":\"Đổi gói đẩy\",\"acknowledgedWarnings\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configs[0].key").value("post_fee"));
    }

    @Test
    void batchWithUnacknowledgedWarningReturnsConflict() throws Exception {
        when(configBatchService.updateBatch(any(), any(), eq(false), any()))
                .thenThrow(new BusinessException(SettingErrorCode.SETTING_CONFIG_WARNING_NOT_ACKNOWLEDGED));
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(put("/api/v1/admin/configs/batch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changes\":[{\"key\":\"boost_fee\",\"value\":\"0\"}],\"reason\":\"Thử\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTING_CONFIG_WARNING_NOT_ACKNOWLEDGED"));
    }

    @Test
    void legacyPathWhenSyncRequiredReturnsConflictWithPreview() throws Exception {
        ConfigPreviewResponse preview = new ConfigPreviewResponse(List.of(), List.of(), List.of());
        when(configService.update(ConfigKey.BOOST_DAYS, "4", "Đổi gói", 1L))
                .thenThrow(new BusinessException(
                        SettingErrorCode.SETTING_CONFIG_SYNC_REQUIRED, null, null, Map.of("preview", preview)));
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(put("/api/v1/admin/configs/boost_days")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"4\",\"reason\":\"Đổi gói\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTING_CONFIG_SYNC_REQUIRED"))
                .andExpect(jsonPath("$.details.preview").exists());
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
