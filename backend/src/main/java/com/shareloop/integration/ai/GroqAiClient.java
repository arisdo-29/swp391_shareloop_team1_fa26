package com.shareloop.integration.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GroqAiClient implements AiClient {
    private static final URI ENDPOINT = URI.create("https://api.groq.com/openai/v1/chat/completions");
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

    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final String systemPrompt;

    @Autowired
    public GroqAiClient(
            ObjectMapper mapper,
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.model:openai/gpt-oss-120b}") String model)
            throws IOException {
        this(
                mapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                apiKey,
                model,
                new String(
                        new ClassPathResource("prompts/item-screening-system.txt")
                                .getInputStream()
                                .readAllBytes(),
                        StandardCharsets.UTF_8));
    }

    GroqAiClient(ObjectMapper mapper, HttpClient httpClient, String apiKey, String model, String systemPrompt) {
        this.mapper = mapper;
        this.httpClient = httpClient;
        this.apiKey = apiKey;
        this.model = model;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public AiResponse screen(AiScreeningContent content, Duration timeout) throws IOException, InterruptedException {
        if (!isAvailable()) {
            throw new IllegalStateException("AI_API_KEY is not configured");
        }
        String body = buildRequestBody(content);
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Groq API returned HTTP " + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body());
        JsonNode message = root.path("choices").path(0).path("message").path("content");
        if (!message.isTextual() || message.asText().isBlank()) {
            throw new IOException("Groq API returned no screening content");
        }
        return new AiResponse(model, message.asText());
    }

    String buildRequestBody(AiScreeningContent content) {
        return mapper.writeValueAsString(Map.of(
                "model",
                model,
                "temperature",
                0,
                "messages",
                new Object[] {
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", mapper.writeValueAsString(content))
                },
                "response_format",
                Map.of(
                        "type",
                        "json_schema",
                        "json_schema",
                        Map.of("name", "item_screening", "strict", true, "schema", mapper.readTree(SCHEMA)))));
    }
}
