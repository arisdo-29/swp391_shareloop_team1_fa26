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
public class OpenRouterSearchClient implements AiSearchClient {
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

    private final OpenRouterGateway gateway;
    private final String systemPrompt;

    @Autowired
    public OpenRouterSearchClient(ObjectMapper mapper, @Value("${app.ai.api-key:}") String apiKey) throws IOException {
        this(
                new OpenRouterGateway(
                        mapper,
                        HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(3))
                                .build(),
                        apiKey),
                new String(
                        new ClassPathResource("prompts/item-search-system.txt")
                                .getInputStream()
                                .readAllBytes(),
                        StandardCharsets.UTF_8));
    }

    OpenRouterSearchClient(OpenRouterGateway gateway, String systemPrompt) {
        this.gateway = gateway;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public boolean isAvailable() {
        return gateway.isAvailable();
    }

    @Override
    public String model() {
        return OpenRouterGateway.MODEL;
    }

    @Override
    public AiResponse extract(String query, String allowedFilters, Duration timeout)
            throws IOException, InterruptedException {
        return gateway.complete(
                systemPrompt + "\nAllowed filters JSON:\n" + allowedFilters,
                query,
                "shareloop_search_filters",
                SCHEMA,
                timeout);
    }
}
