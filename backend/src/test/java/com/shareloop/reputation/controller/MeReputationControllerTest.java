package com.shareloop.reputation.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.reputation.dto.MyReputationResponse;
import com.shareloop.reputation.dto.PointHistoryDto;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.service.ReputationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeReputationController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class MeReputationControllerTest {

    private static final String URL = "/api/v1/me/reputation";
    private static final long USER_ID = 42L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    ReputationService reputationService;

    @Test
    @DisplayName("Chưa đăng nhập trả về 401 Unauthorized")
    void getMyReputation_unauthorized_returns401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Đăng nhập thành công trả về điểm, hạng, sao, tỉ lệ và lịch sử điểm")
    void getMyReputation_authenticated_returns200() throws Exception {
        String token = jwtService.issue(USER_ID, false);
        PointHistoryDto historyDto =
                new PointHistoryDto(10, "REQUEST_COMPLETED", Instant.parse("2026-10-18T10:00:00Z"));
        PageImpl<PointHistoryDto> page = new PageImpl<>(List.of(historyDto), PageRequest.of(0, 20), 1);

        when(reputationService.getMyReputation(eq(USER_ID), anyInt(), anyInt()))
                .thenReturn(new MyReputationResponse(320, Rank.GOLD, 4, 0.92, page));

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(320))
                .andExpect(jsonPath("$.rank").value("GOLD"))
                .andExpect(jsonPath("$.trustStars").value(4))
                .andExpect(jsonPath("$.accuracyRate").value(0.92))
                .andExpect(jsonPath("$.history.content[0].delta").value(10))
                .andExpect(jsonPath("$.history.content[0].reason").value("REQUEST_COMPLETED"));
    }

    @Test
    @DisplayName("Người dùng chưa có giao dịch trả về accuracyRate = null và lịch sử rỗng")
    void getMyReputation_noTransactions_returnsNullAccuracyRate() throws Exception {
        String token = jwtService.issue(USER_ID, false);
        PageImpl<PointHistoryDto> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        when(reputationService.getMyReputation(eq(USER_ID), anyInt(), anyInt()))
                .thenReturn(new MyReputationResponse(0, Rank.BRONZE, 5, null, emptyPage));

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(0))
                .andExpect(jsonPath("$.rank").value("BRONZE"))
                .andExpect(jsonPath("$.trustStars").value(5))
                .andExpect(jsonPath("$.accuracyRate").isEmpty())
                .andExpect(jsonPath("$.history.content").isEmpty());
    }
}
