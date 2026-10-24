package com.shareloop.integration.ai;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenRouterAiClient implements AiClient {
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["contactInfo","prohibited",
            "categoryMismatch","swapIntentInGivePost","qualityIssue","risk","reasonVi"],"properties":{
              "contactInfo":{"type":"object","additionalProperties":false,"required":["found","evidence"],
                "properties":{"found":{"type":"boolean"},"evidence":{"type":["string","null"]}}},
              "prohibited":{"type":"object","additionalProperties":false,
                "required":["found","category","evidence"],"properties":{
                  "found":{"type":"boolean"},"category":{"type":["string","null"]},
                  "evidence":{"type":["string","null"]}}},
              "categoryMismatch":{"type":"boolean"},"swapIntentInGivePost":{"type":"boolean"},
              "qualityIssue":{"type":["string","null"]},
              "risk":{"type":"string","enum":["low","medium","high"]},
              "reasonVi":{"type":"string"}}}
            """;

    private final OpenRouterGateway gateway;
    private final ObjectMapper mapper;
    private final String systemPrompt;

    @Autowired
    public OpenRouterAiClient(ObjectMapper mapper, @Value("${app.ai.api-key:}") String apiKey) throws IOException {
        this(
                new OpenRouterGateway(
                        mapper,
                        HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(3))
                                .build(),
                        apiKey),
                mapper,
                new String(
                        new ClassPathResource("prompts/item-screening-system.txt")
                                .getInputStream()
                                .readAllBytes(),
                        StandardCharsets.UTF_8));
    }

    OpenRouterAiClient(OpenRouterGateway gateway, ObjectMapper mapper, String systemPrompt) {
        this.gateway = gateway;
        this.mapper = mapper;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public boolean isAvailable() {
        return gateway.isAvailable();
    }

    @Override
    public AiResponse screen(AiScreeningContent content, Duration timeout) throws IOException, InterruptedException {
        return gateway.complete(
                systemPrompt, mapper.writeValueAsString(content), "shareloop_item_screening", SCHEMA, timeout);
    }
}
