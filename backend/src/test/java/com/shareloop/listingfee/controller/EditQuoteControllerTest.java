package com.shareloop.listingfee.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.listingfee.ListingFeeErrorCode;
import com.shareloop.listingfee.dto.EditQuote;
import com.shareloop.listingfee.service.PostFeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EditQuoteController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class EditQuoteControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    PostFeeService postFeeService;

    @Test
    void getEditQuote_withoutToken_returns401() throws Exception {
        // given: request không có header Authorization

        // when + then
        mockMvc.perform(get("/api/v1/items/31/edit-quote"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void getEditQuote_withToken_returnsQuoteForTokenUser() throws Exception {
        // given: token của user 10, service báo lượt sửa có phí
        String token = jwtService.issue(10L, false);
        when(postFeeService.quoteEdit(31L, 10L)).thenReturn(new EditQuote(false, 5, EditQuote.Reason.PAID_EDIT));

        // when + then
        mockMvc.perform(get("/api/v1/items/31/edit-quote").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.free").value(false))
                .andExpect(jsonPath("$.amount").value(5))
                .andExpect(jsonPath("$.reason").value("PAID_EDIT"));
    }

    @Test
    void getEditQuote_whenNotOwner_returns403() throws Exception {
        // given
        String token = jwtService.issue(10L, false);
        when(postFeeService.quoteEdit(31L, 10L))
                .thenThrow(new BusinessException(ListingFeeErrorCode.LISTINGFEE_NOT_OWNER));

        // when + then
        mockMvc.perform(get("/api/v1/items/31/edit-quote").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LISTINGFEE_NOT_OWNER"));
    }
}
