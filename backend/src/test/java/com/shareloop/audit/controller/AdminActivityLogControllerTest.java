package com.shareloop.audit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.audit.AuditErrorCode;
import com.shareloop.audit.dto.ActivityLogDto;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.auth.service.JwtService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminActivityLogController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminActivityLogControllerTest {

    private static final String URL = "/api/v1/admin/activity-logs";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    ActivityLogService activityLogService;

    @Test
    @DisplayName("Chưa đăng nhập gọi API admin trả về 401 Unauthorized")
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(activityLogService);
    }

    @Test
    @DisplayName("Người dùng không phải Admin gọi API admin trả về 403 Forbidden")
    void list_nonAdminToken_returns403() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(activityLogService);
    }

    @Test
    @DisplayName("Admin gọi API thành công trả về 200 OK cùng dữ liệu phân trang")
    void list_adminToken_returns200() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        ActivityLogDto logDto = new ActivityLogDto(
                101L, 1L, "APPROVE_ITEM", "ITEM", 42L, null, "Hợp lệ", "Hợp lệ", Instant.parse("2026-10-17T10:00:00Z"));

        when(activityLogService.search(
                        eq(1L), eq("APPROVE_ITEM"), any(LocalDate.class), any(LocalDate.class), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(logDto)));

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + adminToken)
                        .param("actorId", "1")
                        .param("type", "APPROVE_ITEM")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-17")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(101))
                .andExpect(jsonPath("$.content[0].actorId").value(1))
                .andExpect(jsonPath("$.content[0].type").value("APPROVE_ITEM"))
                .andExpect(jsonPath("$.content[0].targetId").value(42));

        verify(activityLogService)
                .search(eq(1L), eq("APPROVE_ITEM"), any(LocalDate.class), any(LocalDate.class), eq(0), eq(20));
    }

    @Test
    @DisplayName("Khoảng ngày không hợp lệ (from > to) trả về 400 Bad Request")
    void list_invalidDateRange_returns400() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        when(activityLogService.search(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new BusinessException(AuditErrorCode.INVALID_DATE_RANGE));

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + adminToken)
                        .param("from", "2026-10-20")
                        .param("to", "2026-10-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }
}
