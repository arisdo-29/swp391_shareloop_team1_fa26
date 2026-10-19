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
public class GeminiSearchClient implements AiSearchClient {
    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,
             "required":["keywords","categoryId","areaId","condition","offerType"],
             "properties":{
               "keywords":{"type":["string","null"]},
               "categoryId":{"type":["integer","null"]},
               "areaId":{"type":["integer","null"]},
               "condition":{"type":["string","null"]},
               "offerType":{"type":["string","null"]}}}
            """;

    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final String systemPrompt;

    @Autowired
    public GeminiSearchClient(
            ObjectMapper mapper,
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.model:gemini-3.5-flash-lite}") String model)
            throws IOException {
        this(
                mapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                apiKey,
                model,
                new String(
                        new ClassPathResource("prompts/item-search-system.txt")
                                .getInputStream()
                                .readAllBytes(),
                        StandardCharsets.UTF_8));
    }

    GeminiSearchClient(ObjectMapper mapper, HttpClient httpClient, String apiKey, String model, String systemPrompt) {
        if (!model.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid Gemini model ID");
        }
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
    public String model() {
        return model;
    }

    @Override
    public AiResponse extract(String query, String allowedFilters, Duration timeout)
            throws IOException, InterruptedException {
        if (!isAvailable()) {
            throw new IllegalStateException("AI_API_KEY is not configured");
        }
        HttpResponse<String> response =
                httpClient.send(buildRequest(query, allowedFilters, timeout), HttpResponse.BodyHandlers.ofString());
        return parseResponse(response.statusCode(), response.body());
    }

    HttpRequest buildRequest(String query, String allowedFilters, Duration timeout) {
        return HttpRequest.newBuilder(URI.create(API_BASE + model + ":generateContent"))
                .timeout(timeout)
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(query, allowedFilters)))
                .build();
    }

    String buildRequestBody(String query, String allowedFilters) {
        String instruction = systemPrompt + "\nAllowed filters JSON:\n" + allowedFilters;
        return mapper.writeValueAsString(Map.of(
                "systemInstruction", Map.of("parts", new Object[] {Map.of("text", instruction)}),
                "contents", new Object[] {Map.of("role", "user", "parts", new Object[] {Map.of("text", query)})},
                "generationConfig",
                        Map.of(
                                "responseFormat",
                                Map.of(
                                        "text",
                                        Map.of("mimeType", "application/json", "schema", mapper.readTree(SCHEMA))))));
    }

    AiResponse parseResponse(int statusCode, String body) throws IOException {
        if (statusCode != 200) {
            throw new IOException("Gemini API returned HTTP " + statusCode);
        }
        JsonNode root = mapper.readTree(body);
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        StringBuilder content = new StringBuilder();
        for (int index = 0; index < parts.size(); index++) {
            JsonNode part = parts.path(index);
            if (!part.path("thought").asBoolean() && part.path("text").isTextual()) {
                content.append(part.path("text").asText());
            }
        }
        if (content.isEmpty()) {
            throw new IOException("Gemini API returned no search filters");
        }
        String modelVersion = root.path("modelVersion").asText();
        return new AiResponse(modelVersion.isBlank() ? model : modelVersion, content.toString());
    }
}
