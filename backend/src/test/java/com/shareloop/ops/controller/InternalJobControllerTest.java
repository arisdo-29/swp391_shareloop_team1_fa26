package com.shareloop.ops.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.listingfee.job.ItemExpiryJob;
import com.shareloop.request.job.RequestJob;
import com.shareloop.review.job.ReviewSlaJob;
import com.shareloop.user.job.PendingAccountCleanupJob;
import com.shareloop.wallet.job.PaymentExpiryJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalJobController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.jobs.enabled=true", "app.jobs.trigger-token=valid-secret-token"})
class InternalJobControllerTest {

    private static final String URL = "/internal/jobs/run";
    private static final String VALID_TOKEN = "valid-secret-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ItemExpiryJob itemExpiryJob;

    @MockitoBean
    private RequestJob requestJob;

    @MockitoBean
    private PendingAccountCleanupJob pendingAccountCleanupJob;

    @MockitoBean
    private PaymentExpiryJob paymentExpiryJob;

    @MockitoBean
    private ReviewSlaJob reviewSlaJob;

    @Test
    @DisplayName("Thiếu header X-Job-Token trả 403 và không job nào chạy")
    void runJobs_withoutToken_returns403() throws Exception {
        mockMvc.perform(post(URL)).andExpect(status().isForbidden());

        verifyNoInteractions(itemExpiryJob, requestJob, pendingAccountCleanupJob, paymentExpiryJob, reviewSlaJob);
    }

    @Test
    @DisplayName("Sai header X-Job-Token trả 403 và không job nào chạy")
    void runJobs_withWrongToken_returns403() throws Exception {
        mockMvc.perform(post(URL).header("X-Job-Token", "wrong-token")).andExpect(status().isForbidden());

        verifyNoInteractions(itemExpiryJob, requestJob, pendingAccountCleanupJob, paymentExpiryJob, reviewSlaJob);
    }

    @Test
    @DisplayName("Đúng token trả 200, đủ 5 job được gọi và trả về ok")
    void runJobs_withValidToken_runsAllJobsSuccessfully() throws Exception {
        mockMvc.perform(post(URL).header("X-Job-Token", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemExpiryJob").value("ok"))
                .andExpect(jsonPath("$.requestJob").value("ok"))
                .andExpect(jsonPath("$.pendingAccountCleanupJob").value("ok"))
                .andExpect(jsonPath("$.paymentExpiryJob").value("ok"))
                .andExpect(jsonPath("$.reviewSlaJob").value("ok"));

        verify(itemExpiryJob).run();
        verify(requestJob).run();
        verify(pendingAccountCleanupJob).run();
        verify(paymentExpiryJob).run();
        verify(reviewSlaJob).run();
    }

    @Test
    @DisplayName("Một job ném exception thì vẫn trả 200, job đó failed và các job khác vẫn chạy bình thường")
    void runJobs_whenOneJobFails_stillRunsRemainingJobs() throws Exception {
        doThrow(new RuntimeException("Simulated job error")).when(requestJob).run();

        mockMvc.perform(post(URL).header("X-Job-Token", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemExpiryJob").value("ok"))
                .andExpect(jsonPath("$.requestJob").value("failed"))
                .andExpect(jsonPath("$.pendingAccountCleanupJob").value("ok"))
                .andExpect(jsonPath("$.paymentExpiryJob").value("ok"))
                .andExpect(jsonPath("$.reviewSlaJob").value("ok"));

        verify(itemExpiryJob).run();
        verify(requestJob).run();
        verify(pendingAccountCleanupJob).run();
        verify(paymentExpiryJob).run();
        verify(reviewSlaJob).run();
    }
}
