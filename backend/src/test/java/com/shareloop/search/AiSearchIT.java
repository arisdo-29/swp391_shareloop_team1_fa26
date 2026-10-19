package com.shareloop.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.common.api.PageResponse;
import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.service.ItemService;
import com.shareloop.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class AiSearchIT extends IntegrationTest {
    @Autowired
    JdbcClient jdbc;

    @Autowired
    ItemService itemService;

    private final List<Long> itemIds = new ArrayList<>();
    private long userId;
    private long categoryId;
    private long areaId;

    @BeforeEach
    void setUp() {
        userId = jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status) "
                        + "VALUES (:email, 'unused', 'Search Test', :phone, 'ACTIVE') RETURNING id")
                .param("email", "search-" + UUID.randomUUID() + "@shareloop.local")
                .param(
                        "phone",
                        "09" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100000000))
                .query(Long.class)
                .single();
        categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
    }

    @AfterEach
    void cleanUp() {
        for (long itemId : itemIds) {
            jdbc.sql("DELETE FROM items WHERE id = :id").param("id", itemId).update();
        }
        jdbc.sql("DELETE FROM users WHERE id = :id").param("id", userId).update();
    }

    @Test
    void approvedActiveItemsAloneMatchAllValidatedFilters() {
        long expected = insert("Sách tiếng Anh", "Sách cũ", "APPROVED", "GIVE", "GOOD", true);
        insert("Sách chưa duyệt", "Sách cũ", "PENDING_REVIEW", "GIVE", "GOOD", true);
        insert("Sách trao đổi", "Sách cũ", "APPROVED", "SWAP", "GOOD", true);
        insert("Sách đã ẩn", "Sách cũ", "APPROVED", "GIVE", "GOOD", false);
        insert("Sách còn mới", "Sách cũ", "APPROVED", "GIVE", "NEW", true);

        PageResponse<ItemResponse> results =
                itemService.searchApprovedForAi(categoryId, areaId, "sach", ItemCondition.GOOD, OfferType.GIVE, 0, 20);

        assertThat(results.totalElements()).isEqualTo(1);
        assertThat(results.items()).extracting(ItemResponse::id).containsExactly(expected);
    }

    @Test
    void nullOptionalFiltersStillSearchApprovedItems() {
        insert("Bàn học", "Bàn còn tốt", "APPROVED", "GIVE", "GOOD", true);
        insert("Bàn gỗ", "Bàn cũ", "APPROVED", "SWAP", "GOOD", true);
        insert("Bàn chờ duyệt", "Bàn còn tốt", "PENDING_REVIEW", "GIVE", "GOOD", true);

        PageResponse<ItemResponse> results = itemService.searchApprovedForAi(null, null, "ban", null, null, 0, 1);

        assertThat(results.totalElements()).isEqualTo(2);
        assertThat(results.items()).hasSize(1);
        assertThat(results.items()).allMatch(item -> item.status().name().equals("APPROVED"));
    }

    private long insert(
            String title, String description, String status, String offerType, String condition, boolean active) {
        long id = jdbc.sql("INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, "
                        + "desired_item, condition, status, is_active) "
                        + "VALUES (:userId, :categoryId, :areaId, :offerType, :title, :description, "
                        + ":desiredItem, :condition, :status, :active) RETURNING id")
                .param("userId", userId)
                .param("categoryId", categoryId)
                .param("areaId", areaId)
                .param("offerType", offerType)
                .param("title", title)
                .param("description", description)
                .param("desiredItem", offerType.equals("SWAP") ? "Bút" : null)
                .param("condition", condition)
                .param("status", status)
                .param("active", active)
                .query(Long.class)
                .single();
        itemIds.add(id);
        return id;
    }
}
