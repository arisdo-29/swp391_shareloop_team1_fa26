package com.shareloop.setting.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdminConfigControllerIT extends IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @Test
    void configUpdateRequiresAdminRole() throws Exception {
        String userToken = jwtService.issue(42, false);

        mockMvc.perform(put("/api/v1/admin/configs/post_fee")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType("application/json")
                        .content("{\"value\":\"8\",\"reason\":\"policy update\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unknownConfigKeyReturnsNotFoundInsteadOfInternalError() throws Exception {
        String adminToken = jwtService.issue(1, true);

        mockMvc.perform(put("/api/v1/admin/configs/not_a_key")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"value\":\"8\",\"reason\":\"policy update\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONFIG_KEY_NOT_FOUND"));
    }
}
