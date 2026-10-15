package com.shareloop.setting.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.setting.dto.LexiconMatch;
import com.shareloop.setting.service.AdminLexiconService;
import com.shareloop.setting.service.LexiconService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminLexiconController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class AdminLexiconControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    AdminLexiconService service;

    @MockitoBean
    LexiconService lexiconService;

    @Test
    void nonAdminCannotReadCreateOrTestLexicon() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(get("/api/v1/admin/lexicon").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/v1/admin/lexicon")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/lexicon/test")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"test\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service, lexiconService);
    }

    @Test
    void blankKeywordIsRejectedByRequestValidation() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(post("/api/v1/admin/lexicon")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\" \",\"group\":\"WEAPONS\",\"level\":\"BLOCK\",\"scope\":\"POST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(service);
    }

    @Test
    void emptyTextIsRejectedByRequestValidation() throws Exception {
        String adminToken = jwtService.issue(1L, true);

        mockMvc.perform(post("/api/v1/admin/lexicon/test")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(lexiconService);
    }

    @Test
    void adminCanTestLexiconAndReceivesExactMatchDetails() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        String text = "súng với zalo";
        org.mockito.Mockito.when(lexiconService.match(text))
                .thenReturn(List.of(
                        new LexiconMatch("súng", "BLOCK", "WEAPONS", LexiconMatch.MatchedAs.EXACT),
                        new LexiconMatch("zalo", "REVIEW", "CONTACTS", LexiconMatch.MatchedAs.EXACT)));

        mockMvc.perform(post("/api/v1/admin/lexicon/test")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].keyword").value("súng"))
                .andExpect(jsonPath("$[0].level").value("BLOCK"))
                .andExpect(jsonPath("$[0].group").value("WEAPONS"))
                .andExpect(jsonPath("$[0].matchedAs").value("EXACT"))
                .andExpect(jsonPath("$[1].keyword").value("zalo"))
                .andExpect(jsonPath("$[1].level").value("REVIEW"))
                .andExpect(jsonPath("$[1].group").value("CONTACTS"))
                .andExpect(jsonPath("$[1].matchedAs").value("EXACT"));
    }

    @Test
    void adminTestLexiconReturnsNormalizedMatchAsReview() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        String text = "ban kinh";
        org.mockito.Mockito.when(lexiconService.match(text))
                .thenReturn(List.of(new LexiconMatch("bán", "REVIEW", "WEAPONS", LexiconMatch.MatchedAs.NORMALIZED)));

        mockMvc.perform(post("/api/v1/admin/lexicon/test")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].keyword").value("bán"))
                .andExpect(jsonPath("$[0].level").value("REVIEW"))
                .andExpect(jsonPath("$[0].group").value("WEAPONS"))
                .andExpect(jsonPath("$[0].matchedAs").value("NORMALIZED"));
    }

    private static String validBody() {
        return "{\"keyword\":\"test\",\"group\":\"WEAPONS\",\"level\":\"BLOCK\",\"scope\":\"POST\"}";
    }
}
