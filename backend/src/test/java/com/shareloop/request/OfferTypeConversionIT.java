package com.shareloop.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.request.dto.CreateRequest;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration test chuyển đổi hình thức Cho ↔ Trao đổi khi gửi yêu cầu và khi chủ bài chọn người (CD-01 … CD-06),
 * chạy trên PostgreSQL thật (Testcontainers).
 */
@AutoConfigureMockMvc
class OfferTypeConversionIT extends IntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final int DONOR_BALANCE = 20;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ConfigService configService;

    private final List<Long> createdRequestIds = new ArrayList<>();
    private final List<Long> createdItemIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long requestId : createdRequestIds) {
            jdbc.update("DELETE FROM requests WHERE id = ?", requestId);
        }
        createdRequestIds.clear();
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM credit_ledger WHERE user_id = ?", userId);
        }
        for (Long itemId : createdItemIds) {
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        createdItemIds.clear();
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM activity_logs WHERE actor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUserIds.clear();
    }

    @Test
    @DisplayName("CD-01: Gửi yêu cầu kèm món tới bài GIVE thành công; itemOfferType = GIVE, conversionFee = POST_FEE")
    void createRequest_withOfferedItemToGiveItem_returnsGiveTypeAndPostFee() throws Exception {
        // given
        long donorId = insertUser(DONOR_BALANCE);
        long receiverId = insertUser(0);
        long giveItemId = insertItem(donorId, "GIVE", "APPROVED");
        long offeredItemId = insertItem(receiverId, "GIVE", "APPROVED");

        // when
        JsonNode response = createRequestViaApi(giveItemId, receiverId, new CreateRequest(offeredItemId));

        // then: requests.type vẫn theo loại bài, chỉ đổi khi chủ bài chọn
        assertThat(response.get("type").asString()).isEqualTo("GIVE");
        assertThat(response.get("offeredItemId").asLong()).isEqualTo(offeredItemId);
        assertThat(response.get("itemOfferType").asString()).isEqualTo("GIVE");
        assertThat(response.get("conversionFee").asInt()).isEqualTo(postFee());
    }

    @Test
    @DisplayName("CD-01: Gửi yêu cầu không kèm món tới bài SWAP thành công; itemOfferType = SWAP, conversionFee = 0")
    void createRequest_withoutOfferedItemToSwapItem_returnsSwapTypeAndZeroFee() throws Exception {
        // given
        long donorId = insertUser(DONOR_BALANCE);
        long receiverId = insertUser(0);
        long swapItemId = insertItem(donorId, "SWAP", "APPROVED");

        // when
        JsonNode response = createRequestViaApi(swapItemId, receiverId, null);

        // then
        assertThat(response.get("type").asString()).isEqualTo("SWAP");
        assertThat(response.get("offeredItemId").isNull()).isTrue();
        assertThat(response.get("itemOfferType").asString()).isEqualTo("SWAP");
        assertThat(response.get("conversionFee").asInt()).isZero();
    }

    @Test
    @DisplayName("selectReceiver GIVE chọn yêu cầu không món: giữ GIVE, không trừ phí")
    void selectReceiver_giveItemWithoutOfferedItem_staysGiveWithoutFee() throws Exception {
        // given
        long donorId = insertUser(DONOR_BALANCE);
        long receiverId = insertUser(0);
        long itemId = insertItem(donorId, "GIVE", "APPROVED");
        long requestId = createRequestViaApi(itemId, receiverId, null).get("id").asLong();

        // when
        selectViaApi(requestId, donorId).andExpect(jsonPath("$.type").value("GIVE"));

        // then
        assertThat(queryItemOfferType(itemId)).isEqualTo("GIVE");
        assertThat(queryItemStatus(itemId)).isEqualTo("RESERVED");
        assertThat(queryCreditBalance(donorId)).isEqualTo(DONOR_BALANCE);
    }

    @Test
    @DisplayName("selectReceiver GIVE chọn yêu cầu có món: chuyển SWAP, trừ POST_FEE, giữ chỗ cả hai món (CD-02)")
    void selectReceiver_giveItemWithOfferedItem_convertsToSwapAndChargesPostFee() throws Exception {
        // given
        long donorId = insertUser(DONOR_BALANCE);
        long receiverId = insertUser(0);
        long itemId = insertItem(donorId, "GIVE", "APPROVED");
        long offeredItemId = insertItem(receiverId, "GIVE", "APPROVED");
        long requestId = createRequestViaApi(itemId, receiverId, new CreateRequest(offeredItemId))
                .get("id")
                .asLong();

        // when
        selectViaApi(requestId, donorId)
                .andExpect(jsonPath("$.type").value("SWAP"))
                .andExpect(jsonPath("$.itemOfferType").value("SWAP"))
                .andExpect(jsonPath("$.conversionFee").value(0));

        // then
        assertThat(queryItemOfferType(itemId)).isEqualTo("SWAP");
        assertThat(queryItemStatus(itemId)).isEqualTo("RESERVED");
        assertThat(queryItemStatus(offeredItemId)).isEqualTo("RESERVED");
        assertThat(queryCreditBalance(donorId)).isEqualTo(DONOR_BALANCE - postFee());
    }

    @Test
    @DisplayName("selectReceiver SWAP chọn yêu cầu có món: giữ SWAP, không trừ phí, giữ chỗ cả hai món")
    void selectReceiver_swapItemWithOfferedItem_staysSwapWithoutFee() throws Exception {
        // given
        long donorId = insertUser(DONOR_BALANCE);
        long receiverId = insertUser(0);
        long itemId = insertItem(donorId, "SWAP", "APPROVED");
        long offeredItemId = insertItem(receiverId, "GIVE", "APPROVED");
        long requestId = createRequestViaApi(itemId, receiverId, new CreateRequest(offeredItemId))
                .get("id")
                .asLong();

        // when
        selectViaApi(requestId, donorId).andExpect(jsonPath("$.type").value("SWAP"));

        // then
        assertThat(queryItemOfferType(itemId)).isEqualTo("SWAP");
        assertThat(queryItemStatus(offeredItemId)).isEqualTo("RESERVED");
        assertThat(queryCreditBalance(donorId)).isEqualTo(DONOR_BALANCE);
    }

    @Test
    @DisplayName("selectReceiver SWAP chọn yêu cầu không món: chuyển GIVE, không hoàn phí (CD-04)")
    void selectReceiver_swapItemWithoutOfferedItem_convertsToGiveWithoutRefund() throws Exception {
        // given
        long donorId = insertUser(DONOR_BALANCE);
        long receiverId = insertUser(0);
        long itemId = insertItem(donorId, "SWAP", "APPROVED");
        long requestId = createRequestViaApi(itemId, receiverId, null).get("id").asLong();

        // when
        selectViaApi(requestId, donorId)
                .andExpect(jsonPath("$.type").value("GIVE"))
                .andExpect(jsonPath("$.itemOfferType").value("GIVE"));

        // then
        assertThat(queryItemOfferType(itemId)).isEqualTo("GIVE");
        assertThat(queryItemStatus(itemId)).isEqualTo("RESERVED");
        assertThat(queryCreditBalance(donorId)).isEqualTo(DONOR_BALANCE);
    }

    // ---------- Tiện ích hỗ trợ test ----------

    private int postFee() {
        return configService.getInt(ConfigKey.POST_FEE);
    }

    private JsonNode createRequestViaApi(long itemId, long receiverId, CreateRequest body) throws Exception {
        var builder = post("/api/v1/items/{itemId}/requests", itemId).header("Authorization", token(receiverId));
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        MvcResult result =
                mockMvc.perform(builder).andExpect(status().isCreated()).andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        createdRequestIds.add(json.get("id").asLong());
        return json;
    }

    private org.springframework.test.web.servlet.ResultActions selectViaApi(long requestId, long donorId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/requests/{id}/select", requestId).header("Authorization", token(donorId)))
                .andExpect(status().isOk());
    }

    private String token(long userId) {
        return "Bearer " + jwtService.issue(userId, false);
    }

    private long insertUser(int creditBalance) {
        int seq = SEQ.incrementAndGet();
        String email = "offer-type-" + seq + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0997%06d", seq);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'hash', 'Offer Type User', ?, 'ACTIVE', ?, 0) RETURNING id",
                Long.class,
                email,
                phone,
                creditBalance);
        createdUserIds.add(userId);
        return userId;
    }

    private long insertItem(long donorId, String offerType, String status) {
        long categoryId = jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
        long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        String desiredItem = "SWAP".equals(offerType) ? "Muốn đổi lấy đồ dùng học tập" : null;
        Long itemId = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, desired_item,"
                        + " condition, status, fee_state, pending_fee, pending_fee_type)"
                        + " VALUES (?, ?, ?, ?, 'Offer Type Item', 'Mô tả', ?, 'GOOD', ?, 'CHARGED', 0, NULL)"
                        + " RETURNING id",
                Long.class,
                donorId,
                categoryId,
                areaId,
                offerType,
                desiredItem,
                status);
        createdItemIds.add(itemId);
        return itemId;
    }

    private String queryItemOfferType(long itemId) {
        return jdbc.queryForObject("SELECT offer_type FROM items WHERE id = ?", String.class, itemId);
    }

    private String queryItemStatus(long itemId) {
        return jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId);
    }

    private int queryCreditBalance(long userId) {
        return jdbc.queryForObject("SELECT credit_balance FROM users WHERE id = ?", Integer.class, userId);
    }
}
