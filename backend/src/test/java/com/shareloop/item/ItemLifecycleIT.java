package com.shareloop.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ItemLifecycleIT extends IntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EntityManager entityManager;

    @Test
    void ownerCanRemovePendingItemAndReleaseHeldFee() throws Exception {
        long owner = user(10, 5);
        long itemId = item(owner, "PENDING_REVIEW", "HELD");

        mvc.perform(delete("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(owner, false)))
                .andExpect(status().isNoContent());
        entityManager.flush();

        assertThat(jdbc.sql("SELECT is_deleted FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(jdbc.sql("SELECT fee_state FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("RELEASED");
        assertThat(jdbc.sql("SELECT held_credit FROM users WHERE id = :id")
                        .param("id", owner)
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    @Test
    void otherUserCannotRemoveItem() throws Exception {
        long owner = user(10, 5);
        long other = user(0, 0);
        long itemId = item(owner, "PENDING_REVIEW", "HELD");

        mvc.perform(delete("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(other, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_OWNER"));

        assertThat(jdbc.sql("SELECT is_deleted FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(Boolean.class)
                        .single())
                .isFalse();
    }

    @Test
    void reservedItemCannotBeRemoved() throws Exception {
        long owner = user(10, 0);
        long itemId = item(owner, "RESERVED", "CHARGED");

        mvc.perform(delete("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(owner, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_REMOVE_NOT_ALLOWED"));

        assertThat(jdbc.sql("SELECT is_deleted FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(Boolean.class)
                        .single())
                .isFalse();
    }

    @Test
    void removeRequiresAuthentication() throws Exception {
        mvc.perform(delete("/api/v1/items/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private long user(int balance, int held) {
        return jdbc.sql(
                        "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit) "
                                + "VALUES (:email, 'unused', 'Test', :phone, 'ACTIVE', :balance, :held) RETURNING id")
                .param("email", "lifecycle-" + UUID.randomUUID() + "@shareloop.local")
                .param(
                        "phone",
                        "09" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100000000))
                .param("balance", balance)
                .param("held", held)
                .query(Long.class)
                .single();
    }

    private long item(long owner, String status, String feeState) {
        long category = jdbc.sql("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long area = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        return jdbc.sql("INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, "
                        + "condition, status, fee_state, pending_fee, pending_fee_type) "
                        + "VALUES (:owner, :category, :area, 'GIVE', 'Book', 'Description', 'GOOD', "
                        + ":status, :feeState, :fee, :feeType) RETURNING id")
                .param("owner", owner)
                .param("category", category)
                .param("area", area)
                .param("status", status)
                .param("feeState", feeState)
                .param("fee", "HELD".equals(feeState) ? 5 : 0)
                .param("feeType", "HELD".equals(feeState) ? "POST" : null)
                .query(Long.class)
                .single();
    }
}
