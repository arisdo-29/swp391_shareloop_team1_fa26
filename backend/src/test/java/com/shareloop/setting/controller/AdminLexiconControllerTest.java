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
<<<<<<< HEAD
import com.shareloop.setting.dto.LexiconMatch;
import com.shareloop.setting.service.AdminLexiconService;
import com.shareloop.setting.service.LexiconService;
import java.util.List;
=======
import com.shareloop.setting.service.AdminLexiconService;
>>>>>>> origin/develop
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

<<<<<<< HEAD
    @MockitoBean
    LexiconService lexiconService;

=======
>>>>>>> origin/develop
    @Test
    void nonAdminCannotReadOrCreateLexicon() throws Exception {
        String userToken = jwtService.issue(42L, false);

        mockMvc.perform(get("/api/v1/admin/lexicon").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/v1/admin/lexicon")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
<<<<<<< HEAD
        mockMvc.perform(post("/api/v1/admin/lexicon/test")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"test\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
        verifyNoInteractions(lexiconService);
=======

        verifyNoInteractions(service);
>>>>>>> origin/develop
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

<<<<<<< HEAD
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
    void adminCanTestLexiconAndReceivesMatchDetails() throws Exception {
        String adminToken = jwtService.issue(1L, true);
        org.mockito.Mockito.when(lexiconService.match("ban kinh"))
                .thenReturn(List.of(new LexiconMatch("bán", "REVIEW", "WEAPONS", LexiconMatch.MatchedAs.NORMALIZED)));

        mockMvc.perform(post("/api/v1/admin/lexicon/test")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"ban kinh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].keyword").value("bán"))
                .andExpect(jsonPath("$[0].level").value("REVIEW"))
                .andExpect(jsonPath("$[0].group").value("WEAPONS"))
                .andExpect(jsonPath("$[0].matchedAs").value("NORMALIZED"));
    }

=======
>>>>>>> origin/develop
    private static String validBody() {
        return "{\"keyword\":\"test\",\"group\":\"WEAPONS\",\"level\":\"BLOCK\",\"scope\":\"POST\"}";
    }
}
