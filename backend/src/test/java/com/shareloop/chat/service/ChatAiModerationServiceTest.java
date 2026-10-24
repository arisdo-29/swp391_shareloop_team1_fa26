package com.shareloop.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.chat.entity.Message;
import com.shareloop.chat.entity.MessageModeration;
import com.shareloop.chat.repository.MessageRepository;
import com.shareloop.integration.ai.AiResponse;
import com.shareloop.integration.ai.ChatAiClient;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ChatAiModerationServiceTest {
    @Mock
    ChatAiClient client;

    @Mock
    ChatContactFilter tierOne;

    @Mock
    ConfigService config;

    @Mock
    MessageRepository messages;

    ChatAiModerationService service;

    @BeforeEach
    void setUp() {
        service = new ChatAiModerationService(client, tierOne, config, messages, new JsonMapper());
    }

    @Test
    void callsAiOnlyForAmbiguousMessageAndBlocksAtConfiguredThreshold() throws Exception {
        when(tierOne.isAmbiguous("ib mình nhé")).thenReturn(true);
        when(client.isAvailable()).thenReturn(true);
        when(config.getDecimal(ConfigKey.CHAT_AI_THRESHOLD)).thenReturn(new BigDecimal("0.7"));
        when(messages.findTop3ByRequestIdAndModerationOrderByIdDesc(7L, MessageModeration.PASS))
                .thenReturn(List.of(new Message(7L, 2L, "tin mới"), new Message(7L, 1L, "tin cũ")));
        when(client.checkContact(any(), any(), any())).thenReturn(new AiResponse("test", """
                        {"containsContact":true,"confidence":0.7,"reasonVi":"Liên hệ ngoài ứng dụng"}
                        """));

        var decision = service.evaluate(7L, "ib mình nhé");

        assertThat(decision).isPresent();
        assertThat(decision.orElseThrow().blocked()).isTrue();
        verify(client).checkContact("ib mình nhé", List.of("tin cũ", "tin mới"), Duration.ofSeconds(5));
    }

    @Test
    void skipsAiForCertainTierOneResult() throws Exception {
        assertThat(service.evaluate(7L, "Mình hẹn ở cổng trường")).isEmpty();
        verify(client, never()).checkContact(any(), any(), any());
    }

    @Test
    void unavailableAiKeepsTierOneResult() throws Exception {
        when(tierOne.isAmbiguous("ib mình nhé")).thenReturn(true);
        assertThat(service.evaluate(7L, "ib mình nhé")).isEmpty();
        verify(client, never()).checkContact(any(), any(), any());
    }

    @Test
    void aiFailureKeepsTierOneResult() throws Exception {
        when(tierOne.isAmbiguous("ib mình nhé")).thenReturn(true);
        when(client.isAvailable()).thenReturn(true);
        when(config.getDecimal(ConfigKey.CHAT_AI_THRESHOLD)).thenReturn(new BigDecimal("0.7"));
        when(messages.findTop3ByRequestIdAndModerationOrderByIdDesc(7L, MessageModeration.PASS))
                .thenReturn(List.of());
        when(client.checkContact(any(), any(), any())).thenThrow(new IOException("timeout"));

        assertThat(service.evaluate(7L, "ib mình nhé")).isEmpty();
    }

    @Test
    void malformedAiResponseKeepsTierOneResult() throws Exception {
        when(tierOne.isAmbiguous("ib mình nhé")).thenReturn(true);
        when(client.isAvailable()).thenReturn(true);
        when(config.getDecimal(ConfigKey.CHAT_AI_THRESHOLD)).thenReturn(new BigDecimal("0.7"));
        when(messages.findTop3ByRequestIdAndModerationOrderByIdDesc(7L, MessageModeration.PASS))
                .thenReturn(List.of());
        when(client.checkContact(any(), any(), any()))
                .thenReturn(new AiResponse("test", "{" + "\"containsContact\":true}"));

        assertThat(service.evaluate(7L, "ib mình nhé")).isEmpty();
    }
}
