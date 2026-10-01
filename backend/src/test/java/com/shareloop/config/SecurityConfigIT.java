package com.shareloop.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.common.security.CurrentUserId;
import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureMockMvc
@Import(SecurityConfigIT.FakeController.class)
class SecurityConfigIT extends IntegrationTest {

    @RestController
    static class FakeController {

        @GetMapping("/api/v1/_test/me")
        Long me(@CurrentUserId Long userId) {
            return userId;
        }

        @GetMapping("/api/v1/admin/_test/ping")
        String adminPing() {
            return "pong";
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    JwtAuthenticationConverter jwtAuthenticationConverter;

    @Autowired
    AuditProbeAreaRepository areaRepository;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        areaRepository.deleteAll();
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void protectedUrlWithoutTokenReturns401InErrorResponseFormat() throws Exception {
        mockMvc.perform(get("/api/v1/_test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void invalidTokenReturns401InErrorResponseFormat() throws Exception {
        mockMvc.perform(get("/api/v1/_test/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void tokenFromJwtServiceReachesProtectedUrl() throws Exception {
        String token = jwtService.issue(42, false);
        mockMvc.perform(get("/api/v1/_test/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }

    @Test
    void nonAdminTokenGets403OnAdminUrl() throws Exception {
        String token = jwtService.issue(42, false);
        mockMvc.perform(get("/api/v1/admin/_test/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void adminTokenReachesAdminUrl() throws Exception {
        String token = jwtService.issue(1, true);
        mockMvc.perform(get("/api/v1/admin/_test/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void publicGetPathsSkipAuthentication() throws Exception {
        // Không có controller: 404 chứng tỏ request đã qua tầng security (không bị chặn 401).
        mockMvc.perform(get("/api/v1/categories/999999")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/areas/none")).andExpect(status().isNotFound());
    }

    @Test
    void savedRecordGetsCreatedByAndUpdatedByFromToken() {
        var jwt = jwtDecoder.decode(jwtService.issue(7, false));
        SecurityContextHolder.getContext().setAuthentication(jwtAuthenticationConverter.convert(jwt));

        var saved = areaRepository.saveAndFlush(new AuditProbeArea("probe", (short) 1));

        assertThat(saved.getCreatedBy()).isEqualTo(7L);
        assertThat(saved.getUpdatedBy()).isEqualTo(7L);
    }

    @Test
    void savedRecordWithoutUserLeavesAuditorEmpty() {
        var saved = areaRepository.saveAndFlush(new AuditProbeArea("probe-job", (short) 1));

        assertThat(saved.getCreatedBy()).isNull();
        assertThat(saved.getUpdatedBy()).isNull();
    }
}
