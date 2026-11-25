package com.shareloop.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.admin.service.AdminItemService;
import com.shareloop.auth.service.JwtService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminItemController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminItemControllerTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @MockitoBean
    AdminItemService adminItemService;

    private static final String ITEMS = "/api/v1/admin/items";
    private static final String BODY = "{\"reason\":\"Vi phạm nội quy\"}";

    @Test
    void anonymousCannotUseAnyEndpoint() throws Exception {
        mvc.perform(get(ITEMS)).andExpect(status().isUnauthorized());
        mvc.perform(get(ITEMS + "/5")).andExpect(status().isUnauthorized());
        mvc.perform(post(ITEMS + "/5/hide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminItemService);
    }

    @Test
    void regularUserGetsForbiddenOnListDetailHideAndUnhide() throws Exception {
        String token = "Bearer " + jwt.issue(42L, false);

        mvc.perform(get(ITEMS).header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get(ITEMS + "/5").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(post(ITEMS + "/5/hide")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(post(ITEMS + "/5/unhide")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminItemService);
    }

    @Test
    void adminListUsesDefaults() throws Exception {
        String token = "Bearer " + jwt.issue(1L, true);
        when(adminItemService.list(
                        any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0));

        mvc.perform(get(ITEMS).header("Authorization", token)).andExpect(status().isOk());

        verify(adminItemService)
                .list(
                        isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false),
                        eq(0), eq(20));
    }

    @Test
    void hide_whenReasonTooShortOrMissing_returns400() throws Exception {
        String token = "Bearer " + jwt.issue(1L, true);

        mvc.perform(post(ITEMS + "/5/hide")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ab\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(ITEMS + "/5/unhide")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adminItemService);
    }

    @Test
    void hide_whenValid_passesAdminIdItemIdAndReason() throws Exception {
        String token = "Bearer " + jwt.issue(1L, true);

        mvc.perform(post(ITEMS + "/5/hide")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());

        verify(adminItemService).hide(1L, 5L, "Vi phạm nội quy");
    }
}
