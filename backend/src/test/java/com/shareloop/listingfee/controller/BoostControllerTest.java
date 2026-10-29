package com.shareloop.listingfee.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.listingfee.ListingFeeErrorCode;
import com.shareloop.listingfee.dto.BoostQuote;
import com.shareloop.listingfee.dto.BoostResponse;
import com.shareloop.listingfee.service.BoostService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BoostController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class BoostControllerTest {

    private static final String URL = "/api/v1/items/31/boost";
    private static final String QUOTE_URL = "/api/v1/items/31/boost-quote";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    BoostService boostService;

    @Test
    void boost_withoutToken_returns401() throws Exception {
        // given: request không có header Authorization

        // when + then
        mockMvc.perform(post(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void boost_withToken_boostsForTokenUserAndReturnsBoostedUntilAndBalance() throws Exception {
        // given: token của user 10, service đẩy bài thành công
        String token = jwtService.issue(10L, false);
        when(boostService.boost(31L, 10L))
                .thenReturn(new BoostResponse(31L, Instant.parse("2026-10-20T08:00:00Z"), 15L));

        // when + then
        mockMvc.perform(post(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(31))
                .andExpect(jsonPath("$.boostedUntil").value("2026-10-20T08:00:00Z"))
                .andExpect(jsonPath("$.creditBalance").value(15));
    }

    @Test
    void boostQuote_withoutToken_returns401() throws Exception {
        // given: request không có header Authorization

        // when + then
        mockMvc.perform(get(QUOTE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void boostQuote_whenNotAllowed_returnsReason() throws Exception {
        // given: token của user 10, service báo không được đẩy
        String token = jwtService.issue(10L, false);
        when(boostService.quoteBoost(31L, 10L))
                .thenReturn(new BoostQuote(5, Instant.parse("2026-11-02T08:00:00Z"), false, "Vượt hạn của bài."));

        // when + then
        mockMvc.perform(get(QUOTE_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(5))
                .andExpect(jsonPath("$.newBoostedUntil").value("2026-11-02T08:00:00Z"))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("Vượt hạn của bài."));
    }

    @Test
    void boost_whenCategoryNotBoostable_returns409() throws Exception {
        // given
        String token = jwtService.issue(10L, false);
        when(boostService.boost(31L, 10L))
                .thenThrow(new BusinessException(ListingFeeErrorCode.LISTINGFEE_CATEGORY_NOT_BOOSTABLE));

        // when + then
        mockMvc.perform(post(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTINGFEE_CATEGORY_NOT_BOOSTABLE"));
    }
}
