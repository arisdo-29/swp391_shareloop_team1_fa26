package com.shareloop.item;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.repository.ItemRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
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

    @Autowired
    ConfigService configService;

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

    @Test
    void searchIgnoresAccentsAndCombinesCategoryAndAreaFilters() throws Exception {
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long otherCategoryId = jdbc.sql(
                        "SELECT id FROM item_categories WHERE is_active = true ORDER BY id OFFSET 1 LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long otherAreaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id OFFSET 1 LIMIT 1")
                .query(Long.class)
                .single();
        long userId = jdbc.sql(
                        "INSERT INTO users (email, password_hash, full_name, phone, status) "
                                + "VALUES ('search-item-test@shareloop.local', 'unused', 'Test', '0900000888', 'ACTIVE') RETURNING id")
                .query(Long.class)
                .single();

        Item titleMatch = item(userId, categoryId, areaId, "Áo khoác mùa đông");
        titleMatch.approve();
        items.saveAndFlush(titleMatch);
        Item descriptionMatch = new Item(
                userId,
                otherCategoryId,
                areaId,
                OfferType.GIVE,
                "Áo len",
                "Có áo khoác",
                null,
                ItemCondition.GOOD,
                null,
                null);
        descriptionMatch.approve();
        items.saveAndFlush(descriptionMatch);
        Item otherArea = item(userId, categoryId, otherAreaId, "Áo khoác nơi khác");
        otherArea.approve();
        items.saveAndFlush(otherArea);
        items.saveAndFlush(item(userId, categoryId, areaId, "Áo khoác chờ duyệt"));
        Item hidden = item(userId, categoryId, areaId, "Áo khoác bị ẩn");
        hidden.approve();
        hidden.deactivate();
        items.saveAndFlush(hidden);
        Item percentTitle = item(userId, categoryId, areaId, "Giảm 50% áo len");
        percentTitle.approve();
        items.saveAndFlush(percentTitle);

        mvc.perform(get("/api/v1/items")
                        .param("q", "ao khoac")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("areaId", String.valueOf(areaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(titleMatch.getId()));

        mvc.perform(get("/api/v1/items").param("q", "ÁO KHOÁC").param("areaId", String.valueOf(areaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(get("/api/v1/items").param("q", "mua dong"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(titleMatch.getId()));

        mvc.perform(get("/api/v1/items")
                        .param("q", "ao khoac")
                        .param("areaId", String.valueOf(areaId))
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value(titleMatch.getId()));

        mvc.perform(get("/api/v1/items").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(percentTitle.getId()));

        mvc.perform(get("/api/v1/items").param("q", "  ").param("areaId", String.valueOf(areaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void filtersByConditionAndOfferTypeTogetherWithKeywordAndCategory() throws Exception {
        // given
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long otherCategoryId = jdbc.sql(
                        "SELECT id FROM item_categories WHERE is_active = true ORDER BY id OFFSET 1 LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long userId = jdbc.sql(
                        "INSERT INTO users (email, password_hash, full_name, phone, status) "
                                + "VALUES ('filter-item-test@shareloop.local', 'unused', 'Test', '0900000777', 'ACTIVE') RETURNING id")
                .query(Long.class)
                .single();
        Item newGive = approvedItem(userId, categoryId, areaId, "Sách cũ loại một", OfferType.GIVE, ItemCondition.NEW);
        approvedItem(userId, categoryId, areaId, "Sách cũ loại hai", OfferType.GIVE, ItemCondition.NEW);
        approvedItem(userId, categoryId, areaId, "Sách cũ đã dùng", OfferType.GIVE, ItemCondition.GOOD);
        approvedItem(userId, categoryId, areaId, "Sách cũ đổi chác", OfferType.SWAP, ItemCondition.NEW);
        approvedItem(userId, otherCategoryId, areaId, "Sách cũ khác danh mục", OfferType.GIVE, ItemCondition.NEW);
        approvedItem(userId, categoryId, areaId, "Bút máy", OfferType.GIVE, ItemCondition.NEW);

        // when và then: đủ bốn bộ lọc, tổng số đúng và phân trang theo kết quả đã lọc
        mvc.perform(get("/api/v1/items")
                        .param("q", "sach cu")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("condition", "NEW")
                        .param("offerType", "GIVE")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(newGive.getId()));

        // không có q thì đi qua truy vấn khác nhưng kết quả phải giống nhau
        mvc.perform(get("/api/v1/items")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("condition", "NEW")
                        .param("offerType", "GIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mvc.perform(get("/api/v1/items").param("condition", "GOOD").param("offerType", "SWAP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/v1/items").param("offerType", "SWAP").param("q", "sach cu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/v1/items").param("condition", "BROKEN")).andExpect(status().isBadRequest());
    }

    @Test
    void publicSearchPromotesConfiguredNumberOfBoostedItemsPerPageWithoutDuplicates() throws Exception {
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long userId = jdbc.sql(
                        "INSERT INTO users (email, password_hash, full_name, phone, status) "
                                + "VALUES ('boost-page-test@shareloop.local', 'unused', 'Boost test', '0900000677', 'ACTIVE') RETURNING id")
                .query(Long.class)
                .single();
        int configuredBoostLimit = configService.getInt(ConfigKey.BOOST_MAX_PER_PAGE);
        int pageSize = Math.min(100, Math.max(1, configuredBoostLimit) + 1);
        int promotedPerPage = Math.min(pageSize, Math.max(0, configuredBoostLimit));
        int boostedCount = promotedPerPage + 1;
        for (int index = 0; index < boostedCount; index++) {
            Item boosted = approvedItem(
                    userId, categoryId, areaId, "Boost paging boosted " + index, OfferType.GIVE, ItemCondition.NEW);
            jdbc.sql("UPDATE items SET boosted_until = now() + interval '1 day' WHERE id = :id")
                    .param("id", boosted.getId())
                    .update();
        }
        int normalCount = pageSize + 2;
        for (int index = 0; index < normalCount; index++) {
            approvedItem(userId, categoryId, areaId, "Boost paging normal " + index, OfferType.GIVE, ItemCondition.NEW);
        }

        mvc.perform(get("/api/v1/items")
                        .param("q", "boost paging")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("page", "0")
                        .param("size", String.valueOf(pageSize)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(boostedCount + normalCount))
                .andExpect(jsonPath("$.items.length()").value(pageSize))
                .andExpect(jsonPath("$.items[0].title").value("Boost paging boosted " + (boostedCount - 1)))
                .andExpect(jsonPath("$.items[" + promotedPerPage + "].title")
                        .value("Boost paging normal " + (normalCount - 1)));

        mvc.perform(get("/api/v1/items")
                        .param("q", "boost paging")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("page", "1")
                        .param("size", String.valueOf(pageSize)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(boostedCount + normalCount))
                .andExpect(jsonPath("$.items.length()").value(pageSize))
                .andExpect(jsonPath("$.items[0].title").value("Boost paging boosted 0"));
    }

    private Item approvedItem(
            long userId, long categoryId, long areaId, String title, OfferType offerType, ItemCondition condition) {
        String desiredItem = offerType == OfferType.SWAP ? "Món mong muốn" : null;
        Item item = new Item(userId, categoryId, areaId, offerType, title, "Mô tả", desiredItem, condition, null, null);
        item.approve();
        return items.saveAndFlush(item);
    }

    private Item item(long userId, long categoryId, long areaId, String title) {
        return new Item(
                userId, categoryId, areaId, OfferType.GIVE, title, "Mô tả", null, ItemCondition.GOOD, null, null);
    }
}
