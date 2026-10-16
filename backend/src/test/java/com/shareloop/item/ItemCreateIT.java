package com.shareloop.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ItemCreateIT extends IntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcClient jdbc;

    private final List<Long> users = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (long userId : users) {
            jdbc.sql("DELETE FROM item_media_mappings WHERE item_id IN (SELECT id FROM items WHERE donor_id = :id)")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM credit_ledger WHERE user_id = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM items WHERE donor_id = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM media_files WHERE created_by = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM users WHERE id = :id").param("id", userId).update();
        }
    }

    @Test
    void cleanPostHoldsFeeAndAttachesImages() throws Exception {
        long owner = user(10);
        long[] images = media(owner);

        mvc.perform(create(owner, body("Sách học", "Sách còn tốt", images)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        long itemId = itemId(owner);
        assertThat(jdbc.sql("SELECT fee_state FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("HELD");
        assertThat(jdbc.sql("SELECT pending_fee FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(5);
        assertThat(held(owner)).isEqualTo(5);
        assertThat(jdbc.sql("SELECT count(*) FROM item_media_mappings WHERE item_id = :id")
                        .param("id", itemId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(3);
    }

    @Test
    void activeLimitCountsPendingAndVisibleApprovedForBothOfferTypes() throws Exception {
        long owner = user(10);
        long[] images = media(owner);
        item(owner, "GIVE", "PENDING_REVIEW", true, false);
        item(owner, "SWAP", "PENDING_REVIEW", true, false);
        long approved = item(owner, "GIVE", "APPROVED", true, false);
        item(owner, "GIVE", "APPROVED", false, false);
        item(owner, "GIVE", "REJECTED", true, false);
        item(owner, "GIVE", "APPROVED", true, true);

        mvc.perform(create(owner, body("Sách học", "Sách còn tốt", images)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_ACTIVE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("3")))
                .andExpect(jsonPath("$.details.limit").value(3))
                .andExpect(jsonPath("$.details.activeCount").value(3));
        assertThat(held(owner)).isZero();

        jdbc.sql("UPDATE items SET is_active = false WHERE id = :id")
                .param("id", approved)
                .update();
        mvc.perform(create(owner, body("Sách học", "Sách còn tốt", images))).andExpect(status().isCreated());
    }

    @Test
    void blockedPostReturnsReasonWithoutHoldingFeeOrAttachingImages() throws Exception {
        long owner = user(10);
        long[] images = media(owner);

        mvc.perform(create(owner, body("Cần sa", "Sách còn tốt", images)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ITEM_MODERATION_BLOCKED"))
                .andExpect(jsonPath("$.field").value("content"))
                .andExpect(jsonPath("$.details.reasonCode").value("LEXICON_BLOCK"));

        assertNoItemOrHold(owner, images);
    }

    @Test
    void reviewPostIsFlaggedForAdmin() throws Exception {
        long owner = user(10);
        long[] images = media(owner);

        mvc.perform(create(owner, body("Sách Zalo", "Sách còn tốt", images))).andExpect(status().isCreated());

        long itemId = itemId(owner);
        assertThat(jdbc.sql("SELECT ai_result ->> 'flag' FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("REVIEW");
        assertThat(jdbc.sql("SELECT ai_result ->> 'reason' FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("LEXICON_REVIEW");
    }

    @Test
    void insufficientCreditRollsBackItemAndLeavesImagesUploaded() throws Exception {
        long owner = user(0);
        long[] images = media(owner);

        mvc.perform(create(owner, body("Sách học", "Sách còn tốt", images)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("WALLET_INSUFFICIENT_BALANCE"));

        assertNoItemOrHold(owner, images);
    }

    @Test
    void mediaFailureAfterFeeHoldRollsBackItemAndWallet() throws Exception {
        long owner = user(10);
        long other = user(0);
        long[] images = media(owner);
        long foreign = media(other)[0];
        images[2] = foreign;

        mvc.perform(create(owner, body("Sách học", "Sách còn tốt", images)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_OWNER"));

        assertNoItemOrHold(owner, images);
    }

    private long user(int balance) {
        long id = jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance) "
                        + "VALUES (:email, 'unused', 'Test', :phone, 'ACTIVE', :balance) RETURNING id")
                .param("email", "item-create-" + UUID.randomUUID() + "@shareloop.local")
                .param(
                        "phone",
                        "09" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100000000))
                .param("balance", balance)
                .query(Long.class)
                .single();
        users.add(id);
        return id;
    }

    private long item(long owner, String offerType, String status, boolean active, boolean deleted) {
        long category = jdbc.sql("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long area = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        return jdbc.sql(
                        "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, "
                                + "desired_item, condition, status, is_active, is_deleted) VALUES (:owner, :category, :area, "
                                + ":offerType, 'Book', 'Description', :desiredItem, 'GOOD', :status, :active, :deleted) RETURNING id")
                .param("owner", owner)
                .param("category", category)
                .param("area", area)
                .param("offerType", offerType)
                .param("desiredItem", "SWAP".equals(offerType) ? "Other book" : null)
                .param("status", status)
                .param("active", active)
                .param("deleted", deleted)
                .query(Long.class)
                .single();
    }

    private long[] media(long owner) {
        long[] ids = new long[3];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = jdbc.sql("INSERT INTO media_files (storage_key, mime_type, size_bytes, created_by) "
                            + "VALUES (gen_random_uuid()::text, 'image/png', 10, :owner) RETURNING id")
                    .param("owner", owner)
                    .query(Long.class)
                    .single();
        }
        return ids;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create(long owner, String body) {
        return post("/api/v1/items")
                .header("Authorization", "Bearer " + jwtService.issue(owner, false))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String body(String title, String description, long[] images) {
        long category = jdbc.sql(
                        "SELECT id FROM item_categories WHERE is_active AND NOT is_restricted ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long area = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        return """
                {"categoryId":%d,"areaId":%d,"offerType":"GIVE","title":"%s",\
                "description":"%s","condition":"GOOD",\
                "images":["/api/v1/media/%d","/api/v1/media/%d","/api/v1/media/%d"]}
                """.formatted(category, area, title, description, images[0], images[1], images[2]);
    }

    private long itemId(long owner) {
        return jdbc.sql("SELECT id FROM items WHERE donor_id = :owner ORDER BY id DESC LIMIT 1")
                .param("owner", owner)
                .query(Long.class)
                .single();
    }

    private int held(long owner) {
        return jdbc.sql("SELECT held_credit FROM users WHERE id = :owner")
                .param("owner", owner)
                .query(Integer.class)
                .single();
    }

    private void assertNoItemOrHold(long owner, long[] images) {
        assertThat(jdbc.sql("SELECT count(*) FROM items WHERE donor_id = :owner")
                        .param("owner", owner)
                        .query(Integer.class)
                        .single())
                .isZero();
        assertThat(held(owner)).isZero();
        for (long image : images) {
            assertThat(jdbc.sql("SELECT status FROM media_files WHERE id = :id")
                            .param("id", image)
                            .query(String.class)
                            .single())
                    .isEqualTo("UPLOADED");
        }
    }
}
