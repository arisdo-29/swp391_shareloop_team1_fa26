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
import com.shareloop.listingfee.dto.RenewQuote;
import com.shareloop.listingfee.dto.RenewResponse;
import com.shareloop.listingfee.service.RenewService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RenewController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class RenewControllerTest {

    private static final String URL = "/api/v1/items/31/renew";
    private static final String QUOTE_URL = "/api/v1/items/31/renew-quote";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    RenewService renewService;

    @Test
    void renew_withoutToken_returns401() throws Exception {
        // given: request không có header Authorization

        // when + then
        mockMvc.perform(post(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void renew_withToken_renewsForTokenUserAndReturnsNewExpireAt() throws Exception {
        // given: token của user 10, service gia hạn thành công
        String token = jwtService.issue(10L, false);
        when(renewService.renew(31L, 10L)).thenReturn(new RenewResponse(31L, 5, Instant.parse("2026-11-14T08:00:00Z")));

        // when + then
        mockMvc.perform(post(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(31))
                .andExpect(jsonPath("$.fee").value(5))
                .andExpect(jsonPath("$.expireAt").value("2026-11-14T08:00:00Z"));
    }

    @Test
    void renewQuote_withoutToken_returns401() throws Exception {
        // given: request không có header Authorization

        // when + then
        mockMvc.perform(get(QUOTE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void renewQuote_withToken_returnsQuoteForTokenUser() throws Exception {
        // given: token của user 10, service trả báo giá được phép
        String token = jwtService.issue(10L, false);
        when(renewService.quoteRenew(31L, 10L))
                .thenReturn(new RenewQuote(5, Instant.parse("2026-11-28T08:00:00Z"), true, null));

        // when + then
        mockMvc.perform(get(QUOTE_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(5))
                .andExpect(jsonPath("$.newExpireAt").value("2026-11-28T08:00:00Z"))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void renew_whenNotOwner_returns403() throws Exception {
        // given
        String token = jwtService.issue(10L, false);
        when(renewService.renew(31L, 10L)).thenThrow(new BusinessException(ListingFeeErrorCode.LISTINGFEE_NOT_OWNER));

        // when + then
        mockMvc.perform(post(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LISTINGFEE_NOT_OWNER"));
    }
}
