package com.shareloop.integration.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shared OpenRouter transport for the three AI features. */
final class OpenRouterGateway {
    static final String PRIMARY_MODEL = "google/gemma-4-31b-it:free";
    static final String FALLBACK_MODEL = "google/gemma-4-26b-a4b-it:free";
    private static final List<String> MODELS = List.of(PRIMARY_MODEL, FALLBACK_MODEL);
    private static final URI ENDPOINT = URI.create("https://openrouter.ai/api/v1/chat/completions");

    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final URI endpoint;

    OpenRouterGateway(ObjectMapper mapper, HttpClient httpClient, String apiKey) {
        this(mapper, httpClient, apiKey, ENDPOINT);
    }

    OpenRouterGateway(ObjectMapper mapper, HttpClient httpClient, String apiKey, URI endpoint) {
        this.mapper = mapper;
        this.httpClient = httpClient;
        this.apiKey = apiKey;
        this.endpoint = endpoint;
    }

    boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    AiResponse complete(String systemPrompt, String userContent, String schemaName, String schema, Duration timeout)
            throws IOException, InterruptedException {
        if (!isAvailable()) {
            throw new IllegalStateException("OPENROUTER_API_KEY is not configured");
        }
        HttpResponse<String> response = httpClient.send(
                buildRequest(systemPrompt, userContent, schemaName, schema, timeout),
                HttpResponse.BodyHandlers.ofString());
        return parseResponse(response.statusCode(), response.body());
    }

    private HttpRequest buildRequest(
            String systemPrompt, String userContent, String schemaName, String schema, Duration timeout) {
        return HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildRequestBody(systemPrompt, userContent, schemaName, schema)))
                .build();
    }

    private String buildRequestBody(String systemPrompt, String userContent, String schemaName, String schema) {
        return mapper.writeValueAsString(Map.of(
                "models", MODELS,
                "messages",
                        List.of(
                                Map.of(
                                        "role",
                                        "system",
                                        "content",
                                        systemPrompt + "\nReturn one JSON object matching " + schemaName + ":\n"
                                                + schema),
                                Map.of("role", "user", "content", userContent)),
                "response_format", Map.of("type", "json_object"),
                "provider", Map.of("require_parameters", true)));
    }

    private AiResponse parseResponse(int statusCode, String body) throws IOException {
        if (statusCode != 200) {
            throw new IOException("OpenRouter API returned HTTP " + statusCode);
        }
        JsonNode root = mapper.readTree(body);
        JsonNode message = root.path("choices").path(0).path("message");
        JsonNode content = message.path("content");
        if (!content.isTextual() || content.asText().isBlank()) {
            throw new IOException("OpenRouter API returned no content");
        }
        String model = root.path("model").asText();
        return new AiResponse(model.isBlank() ? PRIMARY_MODEL : model, content.asText());
    }
}
