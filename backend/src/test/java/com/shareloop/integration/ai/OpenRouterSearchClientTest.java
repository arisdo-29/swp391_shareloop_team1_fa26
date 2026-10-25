package com.shareloop.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class OpenRouterSearchClientTest {
    private static final String RESPONSE = """
            {"model":"google/gemma-4-26b-a4b-it:free","choices":[{"message":{"content":"{}"}}]}
            """;

    @Test
    void sendsSearchPromptAndSchemaThroughOpenRouter() throws Exception {
        try (var server = new OpenRouterTestServer(RESPONSE, 200)) {
            var client = new OpenRouterSearchClient(server.gateway(), "Search system prompt");

            AiResponse result =
                    client.extract("Tìm sách ở Quận 7", "{\"categories\":[],\"districts\":[]}", Duration.ofSeconds(15));

            JsonNode body = server.body();
            assertThat(result.content()).isEqualTo("{}");
            assertThat(result.model()).isEqualTo("google/gemma-4-26b-a4b-it:free");
            assertThat(client.model()).isEqualTo("google/gemma-4-31b-it:free");
            assertThat(server.authorization()).isEqualTo("Bearer test-key");
            assertThat(body.path("models").path(0).asText()).isEqualTo("google/gemma-4-31b-it:free");
            assertThat(body.path("models").path(1).asText()).isEqualTo("google/gemma-4-26b-a4b-it:free");
            assertThat(body.path("messages").path(0).path("content").asText())
                    .contains("Search system prompt", "categories");
            assertThat(body.path("messages").path(1).path("content").asText()).isEqualTo("Tìm sách ở Quận 7");
            assertThat(body.path("response_format")
                            .path("json_schema")
                            .path("schema")
                            .path("required")
                            .size())
                    .isEqualTo(5);
            assertThat(body.path("provider").path("require_parameters").asBoolean())
                    .isTrue();
        }
    }

    @Test
    void propagatesProviderStatusWithoutExposingKey() throws Exception {
        try (var server = new OpenRouterTestServer("{\"error\":{\"message\":\"bad key\"}}", 401)) {
            var client = new OpenRouterSearchClient(server.gateway(), "Prompt");
            assertThatThrownBy(() -> client.extract("sách", "{}", Duration.ofSeconds(15)))
                    .isInstanceOf(IOException.class)
                    .hasMessage("OpenRouter API returned HTTP 401")
                    .hasMessageNotContaining("test-key");
        }
    }

    @Test
    void usesRequestedModelWhenProviderOmitsModel() throws Exception {
        try (var server = new OpenRouterTestServer("{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}", 200)) {
            var client = new OpenRouterSearchClient(server.gateway(), "Prompt");

            AiResponse result = client.extract("sách", "{}", Duration.ofSeconds(15));

            assertThat(result.model()).isEqualTo("google/gemma-4-31b-it:free");
        }
    }
}
