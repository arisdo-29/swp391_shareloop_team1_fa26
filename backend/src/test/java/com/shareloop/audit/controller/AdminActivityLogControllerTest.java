package com.shareloop.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.AuditErrorCode;
import com.shareloop.audit.dto.ActivityLogDto;
import com.shareloop.audit.dto.ActivityTypeDto;
import com.shareloop.audit.service.ActivityLogQueryService;
import com.shareloop.auth.service.JwtService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import io.swagger.v3.oas.annotations.Parameter;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
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
    ActivityLogQueryService activityLogQueryService;

    @Test
    @DisplayName("Chưa đăng nhập gọi API admin trả về 401 Unauthorized")
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(activityLogQueryService);
    }

    @Test
    @DisplayName("Người dùng không phải Admin gọi API admin trả về 403 Forbidden")
    void list_nonAdminToken_returns403() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(activityLogQueryService);
    }

    @Test
    @DisplayName("Admin gọi API thành công trả về 200 OK cùng dữ liệu phân trang")
    void list_adminToken_returns200() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        ActivityLogDto logDto = new ActivityLogDto(
                101L,
                1L,
                "Nguyễn Văn A",
                "a@fpt.edu.vn",
                "REVIEW_APPROVE",
                "Duyệt bài",
                "ITEM",
                42L,
                "Áo khoác size M",
                null,
                null,
                "Duyệt bài",
                Instant.parse("2026-10-17T10:00:00Z"));

        when(activityLogQueryService.search(
                        eq(1L), eq("REVIEW_APPROVE"), any(LocalDate.class), any(LocalDate.class), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(logDto)));

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + adminToken)
                        .param("actorId", "1")
                        .param("type", "REVIEW_APPROVE")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-17")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(101))
                .andExpect(jsonPath("$.content[0].actorId").value(1))
                .andExpect(jsonPath("$.content[0].type").value("REVIEW_APPROVE"))
                .andExpect(jsonPath("$.content[0].targetId").value(42))
                .andExpect(jsonPath("$.content[0].actorName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.content[0].typeLabel").value("Duyệt bài"))
                .andExpect(jsonPath("$.content[0].targetTitle").value("Áo khoác size M"));

        verify(activityLogQueryService)
                .search(eq(1L), eq("REVIEW_APPROVE"), any(LocalDate.class), any(LocalDate.class), eq(0), eq(20));
    }

    @Test
    @DisplayName("Admin lấy danh sách loại hành động trả về [{type, label}]")
    void listTypes_adminToken_returnsTypesWithLabels() throws Exception {
        // given
        String adminToken = jwtService.issue(1L, true);
        when(activityLogQueryService.listTypes())
                .thenReturn(List.of(new ActivityTypeDto("USER_LOCK", "Khoá tài khoản")));

        // when / then
        mockMvc.perform(get(URL + "/types").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("USER_LOCK"))
                .andExpect(jsonPath("$[0].label").value("Khoá tài khoản"));
    }

    @Test
    @DisplayName("Khoảng ngày không hợp lệ (from > to) trả về 400 Bad Request")
    void list_invalidDateRange_returns400() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        when(activityLogQueryService.search(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new BusinessException(AuditErrorCode.INVALID_DATE_RANGE));

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + adminToken)
                        .param("from", "2026-10-20")
                        .param("to", "2026-10-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("Dropdown loại hành động trên Swagger liệt kê đủ mọi ActivityType")
    void typeParameter_allowableValuesMatchActivityTypeEnum() throws Exception {
        Method list = AdminActivityLogController.class.getMethod(
                "list", Long.class, String.class, LocalDate.class, LocalDate.class, int.class, int.class);
        Parameter typeParameter = list.getParameters()[1].getAnnotation(Parameter.class);

        assertThat(typeParameter.schema().allowableValues())
                .containsExactlyInAnyOrder(Arrays.stream(ActivityType.values())
                        .map(ActivityType::code)
                        .toArray(String[]::new));
    }
}
