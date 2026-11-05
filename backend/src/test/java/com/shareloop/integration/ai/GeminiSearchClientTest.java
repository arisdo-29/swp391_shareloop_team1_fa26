package com.shareloop.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GeminiSearchClientTest {
    private final JsonMapper mapper = new JsonMapper();
    private final GeminiSearchClient client = new GeminiSearchClient(
            mapper, HttpClient.newHttpClient(), "test-key", URI.create("https://example.invalid/interactions"));

    @Test
    void sendsStatelessStructuredRequestWithOnlyTheCurrentQueryAndCatalog() {
        JsonNode body = mapper.readTree(
                client.buildRequestBody("Tìm món làm mình nổi bật", "[{\"id\":7,\"title\":\"Vòng tay\"}]"));

        assertThat(body.path("model").asText()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(body.path("store").asBoolean()).isFalse();
        assertThat(body.has("previous_interaction_id")).isFalse();
        assertThat(body.path("response_format").path("mime_type").asText()).isEqualTo("application/json");
        assertThat(body.path("response_format")
                        .path("schema")
                        .path("properties")
                        .has("requestedItem"))
                .isTrue();
        JsonNode input = mapper.readTree(body.path("input").asText());
        assertThat(input.path("query").asText()).isEqualTo("Tìm món làm mình nổi bật");
        assertThat(input.path("availableItems").path(0).path("id").longValue()).isEqualTo(7);
    }

    @Test
    void parsesOnlyCompletedJsonModelOutput() throws Exception {
        AiResponse response = client.parseResponse(200, """
                {"status":"completed","model":"gemini-3.5-flash-lite","steps":[
                  {"type":"model_output","content":[{"type":"text",
                    "text":"{\\"inScope\\":true,\\"itemIds\\":[7],\\"message\\":\\"Có món hợp\\"}"}]}
                ]}
                """);

        assertThat(mapper.readTree(response.content()).path("itemIds").path(0).longValue())
                .isEqualTo(7);
        assertThatThrownBy(() -> client.parseResponse(429, "{}")).hasMessage("Gemini API returned HTTP 429");
    }
}
