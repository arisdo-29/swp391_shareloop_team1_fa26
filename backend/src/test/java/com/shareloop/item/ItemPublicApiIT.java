package com.shareloop.item;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.repository.ItemRepository;
import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ItemPublicApiIT extends IntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ItemRepository items;

    @Test
    void anonymousListFiltersOutPendingAndHiddenItems() throws Exception {
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long userId = jdbc.sql(
                        "INSERT INTO users (email, password_hash, full_name, phone, status) "
                                + "VALUES ('public-item-test@shareloop.local', 'unused', 'Test', '0900000999', 'ACTIVE') RETURNING id")
                .query(Long.class)
                .single();

        Item approved = item(userId, categoryId, areaId, "Bài đã duyệt");
        approved.approve();
        items.saveAndFlush(approved);
        items.saveAndFlush(item(userId, categoryId, areaId, "Bài chờ duyệt"));
        Item hidden = item(userId, categoryId, areaId, "Bài bị ẩn");
        hidden.approve();
        hidden.deactivate();
        items.saveAndFlush(hidden);

        mvc.perform(get("/api/v1/items")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("areaId", String.valueOf(areaId))
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Bài đã duyệt"));
    }

    @Test
    void invalidPaginationReturnsClientError() throws Exception {
        mvc.perform(get("/api/v1/items").param("page", "-1")).andExpect(status().isBadRequest());
    }

    private Item item(long userId, long categoryId, long areaId, String title) {
        return new Item(
                userId, categoryId, areaId, OfferType.GIVE, title, "Mô tả", null, ItemCondition.GOOD, null, null);
    }
}
