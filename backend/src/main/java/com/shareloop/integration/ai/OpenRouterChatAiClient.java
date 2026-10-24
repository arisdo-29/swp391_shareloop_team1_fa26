package com.shareloop.integration.ai;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenRouterChatAiClient implements ChatAiClient {
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,
             "required":["containsContact","confidence","reasonVi"],
             "properties":{"containsContact":{"type":"boolean"},
               "confidence":{"type":"number","minimum":0,"maximum":1},
               "reasonVi":{"type":"string"}}}
            """;

    private final OpenRouterGateway gateway;
    private final ObjectMapper mapper;
    private final String systemPrompt;

    @Autowired
    public OpenRouterChatAiClient(ObjectMapper mapper, @Value("${app.ai.api-key:}") String apiKey) throws IOException {
        this(
                new OpenRouterGateway(
                        mapper,
                        HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(3))
                                .build(),
                        apiKey),
                mapper,
                new String(
                        new ClassPathResource("prompts/chat-moderation-system.txt")
                                .getInputStream()
                                .readAllBytes(),
                        StandardCharsets.UTF_8));
    }

    OpenRouterChatAiClient(OpenRouterGateway gateway, ObjectMapper mapper, String systemPrompt) {
        this.gateway = gateway;
        this.mapper = mapper;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public boolean isAvailable() {
        return gateway.isAvailable();
    }

    @Override
    public AiResponse checkContact(String message, List<String> context, Duration timeout)
            throws IOException, InterruptedException {
        String userData = mapper.writeValueAsString(Map.of("message", message, "previousMessages", context));
        return gateway.complete(systemPrompt, userData, "shareloop_chat_moderation", SCHEMA, timeout);
    }
}
