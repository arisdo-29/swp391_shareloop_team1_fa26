package com.shareloop.review.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.review.dto.ReviewQueueResponse;
import com.shareloop.review.service.ReviewService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminReviewController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminReviewControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    ReviewService reviewService;

    @Test
    void pendingQueueReturnsChecklistForAdmin() throws Exception {
        when(reviewService.listPendingWithChecklist(0, 20))
                .thenReturn(new ReviewQueueResponse(List.of(), List.of("Ảnh đúng món"), 0, 20));
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(get("/api/v1/admin/reviews/pending").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.checklist[0]").value("Ảnh đúng món"));
    }

    @Test
    void nonAdminCannotApproveReview() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(post("/api/v1/admin/reviews/8/approve")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[true]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectWithoutReasonReturnsBadRequest() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(post("/api/v1/admin/reviews/8/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void nonAdminCannotGetRejectReasons() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(get("/api/v1/admin/reviews/reject-reasons").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectReasonsReturnsListForAdmin() throws Exception {
        when(reviewService.listRejectReasons()).thenReturn(List.of("Ảnh không đúng món", "Chứa thông tin liên hệ"));
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(get("/api/v1/admin/reviews/reject-reasons").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Ảnh không đúng món"))
                .andExpect(jsonPath("$[1]").value("Chứa thông tin liên hệ"));
    }
}
