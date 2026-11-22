package com.shareloop.admin.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.admin.dto.AdminRequestMessages;
import com.shareloop.admin.service.AdminRequestDetailService;
import com.shareloop.auth.service.JwtService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.request.RequestErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminRequestDetailController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminRequestDetailControllerTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @MockitoBean
    AdminRequestDetailService adminRequestDetailService;

    private static final String REQUESTS = "/api/v1/admin/requests";

    @Test
    void anonymousGets401AndRegularUserGets403OnDetailAndMessages() throws Exception {
        String userToken = "Bearer " + jwt.issue(42L, false);

        mvc.perform(get(REQUESTS + "/5")).andExpect(status().isUnauthorized());
        mvc.perform(get(REQUESTS + "/5/messages")).andExpect(status().isUnauthorized());
        mvc.perform(get(REQUESTS + "/5").header("Authorization", userToken)).andExpect(status().isForbidden());
        mvc.perform(get(REQUESTS + "/5/messages").header("Authorization", userToken))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminRequestDetailService);
    }

    @Test
    void adminMessages_passesAdminIdAndRequestId() throws Exception {
        String token = "Bearer " + jwt.issue(1L, true);
        when(adminRequestDetailService.getMessages(1L, 5L)).thenReturn(new AdminRequestMessages(List.of()));

        mvc.perform(get(REQUESTS + "/5/messages").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        verify(adminRequestDetailService).getMessages(1L, 5L);
    }

    @Test
    void adminMessages_whenNotViewable_returns409WithErrorCode() throws Exception {
        String token = "Bearer " + jwt.issue(1L, true);
        when(adminRequestDetailService.getMessages(1L, 5L))
                .thenThrow(new BusinessException(RequestErrorCode.REQUEST_MESSAGES_NOT_VIEWABLE));

        mvc.perform(get(REQUESTS + "/5/messages").header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_MESSAGES_NOT_VIEWABLE"));
    }
}
