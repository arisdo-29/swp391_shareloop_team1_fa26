package com.shareloop.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.shareloop.request.dto.CancelRequestInfo;
import com.shareloop.request.dto.RequestDeadlines;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.support.IntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Các nhóm trường mới của RequestResponse (đề nghị huỷ, dời lịch, giao hàng, mốc hạn) phải ra JSON phẳng, trường cũ
 * không đổi tên hay vị trí, và tài liệu OpenAPI sinh đúng.
 */
@AutoConfigureMockMvc
class RequestResponseJsonIT extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MockMvc mockMvc;

    @Test
    void serialize_whenResponseHasExtraGroups_producesFlatJsonAndKeepsOldFields() {
        // given
        Instant now = Instant.parse("2026-12-01T10:00:00Z");
        RequestResponse response = new RequestResponse(
                        5L,
                        1L,
                        10L,
                        null,
                        "GIVE",
                        "LOGISTICS_CONFIRMED",
                        "IN_PERSON",
                        "FPT",
                        now,
                        null,
                        null,
                        null,
                        null,
                        now,
                        null,
                        null,
                        null,
                        now)
                .withExtras(
                        new CancelRequestInfo(20L, now, "BUSY", "Tôi bận đột xuất", null),
                        null,
                        null,
                        new RequestDeadlines(null, null, now, null, null));

        // when
        JsonNode json = objectMapper.valueToTree(response);

        // then: trường cũ vẫn nằm cùng chỗ, trường mới nằm cùng cấp (không có đối tượng lồng)
        assertThat(json.get("id").asLong()).isEqualTo(5L);
        assertThat(json.get("status").asText()).isEqualTo("LOGISTICS_CONFIRMED");
        assertThat(json.get("meetingPlace").asText()).isEqualTo("FPT");
        assertThat(json.get("cancelRequestedBy").asLong()).isEqualTo(20L);
        assertThat(json.get("cancelRequestLabel").asText()).isEqualTo("Tôi bận đột xuất");
        assertThat(json.has("cancelRequestExpiresAt")).isTrue();
        assertThat(json.has("rescheduleCount")).isTrue();
        assertThat(json.has("donorTrackingCode")).isTrue();
        assertThat(json.has("cancelRequest")).isFalse();
        assertThat(json.has("reschedule")).isFalse();
        assertThat(json.has("shipping")).isFalse();
        assertThat(json.has("deadlines")).isFalse();
    }

    @Test
    void openApi_whenGenerated_listsNewFieldsFlatInRequestResponseSchema() throws Exception {
        // when
        String body =
                mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();

        // then
        JsonNode properties = objectMapper.readTree(body).at("/components/schemas/RequestResponse/properties");
        assertThat(properties.has("id")).isTrue();
        assertThat(properties.has("cancelRequestedBy")).isTrue();
        assertThat(properties.has("cancelRequestExpiresAt")).isTrue();
        assertThat(properties.has("deadlines")).isFalse();
    }
}
