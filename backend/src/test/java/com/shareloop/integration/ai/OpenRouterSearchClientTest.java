package com.shareloop.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class OpenRouterSearchClientTest {
    private static final String RESPONSE = """
            {"model":"cohere/north-mini-code:free","choices":[{"message":{"tool_calls":[{"function":{"name":"shareloop_search_filters","arguments":"{}"}}]}}]}
            """;

    @Test
    void sendsSearchPromptAndSchemaThroughOpenRouter() throws Exception {
        try (var server = new OpenRouterTestServer(RESPONSE, 200)) {
            var client = new OpenRouterSearchClient(server.gateway(), "Search system prompt");

            AiResponse result =
                    client.extract("Tìm sách ở Quận 7", "{\"categories\":[],\"districts\":[]}", Duration.ofSeconds(15));

            JsonNode body = server.body();
            assertThat(result.content()).isEqualTo("{}");
            assertThat(result.model()).isEqualTo("cohere/north-mini-code:free");
            assertThat(client.model()).isEqualTo("cohere/north-mini-code:free");
            assertThat(server.authorization()).isEqualTo("Bearer test-key");
            assertThat(body.path("model").asText()).isEqualTo("cohere/north-mini-code:free");
            assertThat(body.path("messages").path(0).path("content").asText())
                    .contains("Search system prompt", "categories", "shareloop_search_filters", "\"required\"");
            assertThat(body.path("messages").path(1).path("content").asText()).isEqualTo("Tìm sách ở Quận 7");
            assertThat(body.has("response_format")).isFalse();
            assertThat(body.path("tools").path(0).path("function").path("name").asText())
                    .isEqualTo("shareloop_search_filters");
            assertThat(body.path("tools")
                            .path(0)
                            .path("function")
                            .path("parameters")
                            .path("type")
                            .asText())
                    .isEqualTo("object");
            assertThat(body.path("tool_choice").path("function").path("name").asText())
                    .isEqualTo("shareloop_search_filters");
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
        try (var server = new OpenRouterTestServer(
                "{\"choices\":[{\"message\":{\"tool_calls\":[{\"function\":{\"name\":\"shareloop_search_filters\",\"arguments\":\"{}\"}}]}}]}",
                200)) {
            var client = new OpenRouterSearchClient(server.gateway(), "Prompt");

            AiResponse result = client.extract("sách", "{}", Duration.ofSeconds(15));

            assertThat(result.model()).isEqualTo("cohere/north-mini-code:free");
        }
    }

    @Test
    void rejectsTextResponseWithoutRequiredToolCall() throws Exception {
        try (var server = new OpenRouterTestServer("{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}", 200)) {
            var client = new OpenRouterSearchClient(server.gateway(), "Prompt");

            assertThatThrownBy(() -> client.extract("sách", "{}", Duration.ofSeconds(15)))
                    .isInstanceOf(IOException.class)
                    .hasMessage("OpenRouter API returned no single tool call");
        }
    }

    @Test
    void rejectsToolCallForUnexpectedFunction() throws Exception {
        String response = """
                {"choices":[{"message":{"tool_calls":[{"function":{"name":"other_function","arguments":"{}"}}]}}]}
                """;
        try (var server = new OpenRouterTestServer(response, 200)) {
            var client = new OpenRouterSearchClient(server.gateway(), "Prompt");

            assertThatThrownBy(() -> client.extract("sách", "{}", Duration.ofSeconds(15)))
                    .isInstanceOf(IOException.class)
                    .hasMessage("OpenRouter API returned invalid tool arguments");
        }
    }
}
