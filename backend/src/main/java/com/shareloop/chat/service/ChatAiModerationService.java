package com.shareloop.chat.service;

import com.shareloop.chat.entity.Message;
import com.shareloop.chat.entity.MessageModeration;
import com.shareloop.chat.repository.MessageRepository;
import com.shareloop.integration.ai.AiResponse;
import com.shareloop.integration.ai.ChatAiClient;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ChatAiModerationService {
    private static final Logger log = LoggerFactory.getLogger(ChatAiModerationService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ChatAiClient aiClient;
    private final ChatContactFilter tierOne;
    private final ConfigService configService;
    private final MessageRepository messageRepository;
    private final ObjectMapper mapper;

    public ChatAiModerationService(
            ChatAiClient aiClient,
            ChatContactFilter tierOne,
            ConfigService configService,
            MessageRepository messageRepository,
            ObjectMapper mapper) {
        this.aiClient = aiClient;
        this.tierOne = tierOne;
        this.configService = configService;
        this.messageRepository = messageRepository;
        this.mapper = mapper;
    }

    /** Returns no decision when tier one is certain or AI is unavailable, preserving its result. */
    public Optional<Decision> evaluate(long requestId, String content) {
        if (!tierOne.isAmbiguous(content) || !aiClient.isAvailable()) {
            return Optional.empty();
        }
        try {
            BigDecimal threshold = configService.getDecimal(ConfigKey.CHAT_AI_THRESHOLD);
            List<String> context =
                    messageRepository
                            .findTop3ByRequestIdAndModerationOrderByIdDesc(requestId, MessageModeration.PASS)
                            .stream()
                            .map(Message::getContent)
                            .filter(value -> value != null && !value.isBlank())
                            .toList();
            context = new java.util.ArrayList<>(context);
            Collections.reverse(context);
            AiResponse response = aiClient.checkContact(content, context, TIMEOUT);
            JsonNode result = mapper.readTree(response.content());
            JsonNode containsContact = result.path("containsContact");
            JsonNode confidenceNode = result.path("confidence");
            JsonNode reasonNode = result.path("reasonVi");
            if (!containsContact.isBoolean() || !confidenceNode.isNumber() || !reasonNode.isTextual()) {
                throw new IllegalArgumentException("Invalid chat moderation response");
            }
            BigDecimal confidence = confidenceNode.decimalValue();
            if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Invalid chat moderation confidence");
            }
            boolean block = containsContact.asBoolean() && confidence.compareTo(threshold) >= 0;
            String reason = reasonNode.asText().trim();
            if (reason.isBlank()) {
                reason = "thông tin liên hệ";
            }
            if (reason.length() > 100) {
                reason = reason.substring(0, 100);
            }
            return Optional.of(new Decision(block, confidence.setScale(2, RoundingMode.HALF_UP), reason));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Chat AI moderation interrupted for request {}", requestId);
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Chat AI moderation unavailable for request {}: {}", requestId, exception.toString());
            return Optional.empty();
        }
    }

    public record Decision(boolean blocked, BigDecimal confidence, String reason) {}
}
