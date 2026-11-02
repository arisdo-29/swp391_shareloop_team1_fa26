package com.shareloop.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GeminiDescriptionClientTest {
    private final JsonMapper mapper = new JsonMapper();
    private final GeminiDescriptionClient client = new GeminiDescriptionClient(
            mapper, HttpClient.newHttpClient(), "test-key", URI.create("https://example.invalid/interactions"));

    @Test
    void sendsOnlyTitleWithoutPhotosOrConversationHistory() {
        JsonNode request = mapper.readTree(client.buildRequestBody("Đồng hồ Casio"));
        assertThat(request.path("model").asText()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(request.path("store").asBoolean()).isFalse();
        assertThat(request.has("previous_interaction_id")).isFalse();
        JsonNode input = mapper.readTree(request.path("input").asText());
        assertThat(input.path("title").asText()).isEqualTo("Đồng hồ Casio");
        assertThat(input.size()).isEqualTo(1);
    }

    @Test
    void acceptsOnlyCompletedJsonOutput() throws Exception {
        assertThat(client.parseResponse(200, """
                {"status":"completed","steps":[{"type":"model_output","content":[
                  {"type":"text","text":"{\\"itemType\\":\\"Đồng hồ\\"}"}]}]}
                """)).contains("Đồng hồ");
        assertThatThrownBy(() -> client.parseResponse(429, "{}")).hasMessage("Gemini API returned HTTP 429");
    }
}
