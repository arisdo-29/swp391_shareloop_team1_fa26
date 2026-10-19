package com.shareloop.setting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.setting.dto.AdminChecklistEntry;
import com.shareloop.setting.service.AdminChecklistService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminChecklistController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminChecklistControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    AdminChecklistService service;

    @Test
    void nonAdminCannotAccessChecklistEndpoints() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(get("/api/v1/admin/checklist").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/checklist")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Câu hỏi?\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/checklist/1")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Câu hỏi?\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/admin/checklist/1/active")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/checklist/reorder")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemIds\":[1,2]}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void blankQuestionIsRejectedByRequestValidation() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(post("/api/v1/admin/checklist")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(service);
    }

    @Test
    void adminCanCreateChecklistEntry() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        when(service.create(any(), any())).thenReturn(new AdminChecklistEntry(1L, "chk_1", "Câu hỏi mới?", 10, true));

        mockMvc.perform(post("/api/v1/admin/checklist")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Câu hỏi mới?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.question").value("Câu hỏi mới?"))
                .andExpect(jsonPath("$.sortOrder").value(10))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void adminCanReorderChecklist() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        when(service.reorder(any(), any()))
                .thenReturn(List.of(
                        new AdminChecklistEntry(2L, "chk_2", "Câu 2", 10, true),
                        new AdminChecklistEntry(1L, "chk_1", "Câu 1", 20, true)));

        mockMvc.perform(put("/api/v1/admin/checklist/reorder")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemIds\":[2,1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[1].id").value(1));
    }

    @Test
    void adminCanToggleActive() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        when(service.setActive(anyLong(), anyBoolean(), any()))
                .thenReturn(new AdminChecklistEntry(1L, "chk_1", "Câu 1", 10, false));

        mockMvc.perform(patch("/api/v1/admin/checklist/1/active")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
