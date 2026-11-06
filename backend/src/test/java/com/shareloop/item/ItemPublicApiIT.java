package com.shareloop.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shareloop.auth.service.JwtService;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.repository.ItemRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
    JwtService jwtService;

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
    void categoryCountMatchesBrowseAcrossBoostPagesAndExcludesExpiredItems() throws Exception {
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long userId = jdbc.sql(
                        "INSERT INTO users (email, password_hash, full_name, phone, status) "
                                + "VALUES ('category-count-test@shareloop.local', 'unused', 'Test', '0900000888', 'ACTIVE') RETURNING id")
                .query(Long.class)
                .single();
        for (int number = 0; number < 5; number++) {
            Item entry = item(userId, categoryId, areaId, "category-count-unique " + number);
            entry.approve();
            items.saveAndFlush(entry);
            if (number < 4) {
                jdbc.sql("UPDATE items SET boosted_until = CURRENT_TIMESTAMP + INTERVAL '1 day', "
                                + "last_boosted_at = CURRENT_TIMESTAMP WHERE id = :id")
                        .param("id", entry.getId())
                        .update();
            }
        }
        Item expired = item(userId, categoryId, areaId, "category-count-unique expired");
        expired.approve();
        items.saveAndFlush(expired);
        jdbc.sql("UPDATE items SET expire_at = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE id = :id")
                .param("id", expired.getId())
                .update();
        Item hidden = item(userId, categoryId, areaId, "category-count-unique hidden");
        hidden.approve();
        hidden.deactivate();
        items.saveAndFlush(hidden);
        items.saveAndFlush(item(userId, categoryId, areaId, "category-count-unique pending"));

        String browseBody = mvc.perform(get("/api/v1/items").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String categoryBody = mvc.perform(get("/api/v1/categories/{id}", categoryId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number browseCount = JsonPath.read(browseBody, "$.totalElements");
        Number categoryCount = JsonPath.read(categoryBody, "$.activeItemCount");
        assertThat(categoryCount.longValue()).isEqualTo(browseCount.longValue());
        mvc.perform(get("/api/v1/items")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("q", "category-count-unique")
                        .param("size", "2")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void invalidPaginationReturnsClientError() throws Exception {
        mvc.perform(get("/api/v1/items").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/items").param("sort", "other")).andExpect(status().isBadRequest());
    }

    @Test
    void expiringSort_keepsRotatedBoostsFirstAndOrdersRegularItemsByExpiry() throws Exception {
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long userId = jdbc.sql(
                        "INSERT INTO users (email, password_hash, full_name, phone, status) "
                                + "VALUES ('expiring-sort-test@shareloop.local', 'unused', 'Test', '0900000780', 'ACTIVE') RETURNING id")
                .query(Long.class)
                .single();
        for (int number = 0; number < 5; number++) {
            Item entry = item(userId, categoryId, areaId, "Bài expiring-sort-unique " + number);
            entry.approve();
            items.saveAndFlush(entry);
            if (number != 4) {
                jdbc.sql("UPDATE items SET expire_at = CURRENT_TIMESTAMP + (:days * INTERVAL '1 day') "
                                + "WHERE id = :id")
                        .param("id", entry.getId())
                        .param("days", number == 0 ? 5 : number == 1 ? 2 : number == 2 ? 4 : 1)
                        .update();
            }
            if (number < 2) {
                jdbc.sql("UPDATE items SET boosted_until = CURRENT_TIMESTAMP + INTERVAL '10 days' WHERE id = :id")
                        .param("id", entry.getId())
                        .update();
            }
        }

        // rotationSeed = 0: vòng xoay bắt đầu ở bài đẩy có id nhỏ nhất, nên kết quả cố định.
        // Trang 0: hai bài đẩy theo thứ tự vòng (0, 1); các trang sau: bài thường sắp hết hạn trước (3, 2, rồi 4 không
        // hạn).
        mvc.perform(get("/api/v1/items")
                        .param("q", "expiring-sort-unique")
                        .param("sort", "expiring")
                        .param("size", "2")
                        .param("rotationSeed", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].title").value("Bài expiring-sort-unique 0"))
                .andExpect(jsonPath("$.items[1].title").value("Bài expiring-sort-unique 1"));
        mvc.perform(get("/api/v1/items")
                        .param("q", "expiring-sort-unique")
                        .param("sort", "expiring")
                        .param("size", "2")
                        .param("page", "1")
                        .param("rotationSeed", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Bài expiring-sort-unique 3"))
                .andExpect(jsonPath("$.items[1].title").value("Bài expiring-sort-unique 2"));
        mvc.perform(get("/api/v1/items")
                        .param("q", "expiring-sort-unique")
                        .param("sort", "expiring")
                        .param("size", "2")
                        .param("page", "2")
                        .param("rotationSeed", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Bài expiring-sort-unique 4"));
    }

    @Test
    void boostedItemsLeadEachFilteredPageInRotationOrderWithoutDuplicates() throws Exception {
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long userId = jdbc.sql(
                        "INSERT INTO users (email, password_hash, full_name, phone, status) "
                                + "VALUES ('boost-list-test@shareloop.local', 'unused', 'Test', '0900000777', 'ACTIVE') RETURNING id")
                .query(Long.class)
                .single();
        for (int number = 0; number < 7; number++) {
            Item item = item(userId, categoryId, areaId, "Bài boost-filter-unique " + number);
            item.approve();
            items.saveAndFlush(item);
            if (number < 4) {
                jdbc.sql("UPDATE items SET boosted_until = CURRENT_TIMESTAMP + INTERVAL '1 day' WHERE id = :id")
                        .param("id", item.getId())
                        .update();
            }
        }

        // 4 bài đẩy (0..3), cỡ trang 4, tối đa 3 bài đẩy mỗi trang, rotationSeed = 0 (vòng bắt đầu ở bài 0).
        // Trang 0: bài đẩy 0, 1, 2 rồi bài thường mới nhất (6); trang 1: bài đẩy 3 rồi bài thường còn lại (5, 4).
        mvc.perform(get("/api/v1/items")
                        .param("q", "boost-filter-unique")
                        .param("page", "0")
                        .param("size", "4")
                        .param("rotationSeed", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.rotationSeed").value(0))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].title").value("Bài boost-filter-unique 0"))
                .andExpect(jsonPath("$.items[1].title").value("Bài boost-filter-unique 1"))
                .andExpect(jsonPath("$.items[2].title").value("Bài boost-filter-unique 2"))
                .andExpect(jsonPath("$.items[3].title").value("Bài boost-filter-unique 6"));
        mvc.perform(get("/api/v1/items")
                        .param("q", "boost-filter-unique")
                        .param("page", "1")
                        .param("size", "4")
                        .param("rotationSeed", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].title").value("Bài boost-filter-unique 3"))
                .andExpect(jsonPath("$.items[1].title").value("Bài boost-filter-unique 5"))
                .andExpect(jsonPath("$.items[2].title").value("Bài boost-filter-unique 4"));
        mvc.perform(get("/api/v1/items").param("q", "boost-filter-unique").param("boosted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.items.length()").value(4));
    }

    @Test
    void browseDefaultsToTwentyFourItemsAndOnlyLastPageIsShort() throws Exception {
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long userId = jdbc.sql(
                        "INSERT INTO users (email, password_hash, full_name, phone, status) "
                                + "VALUES ('browse-24-test@shareloop.local', 'unused', 'Test', '0900000778', 'ACTIVE') RETURNING id")
                .query(Long.class)
                .single();
        for (int number = 0; number < 49; number++) {
            Item entry = item(userId, categoryId, areaId, "Bài browse-24-unique " + number);
            entry.approve();
            items.saveAndFlush(entry);
        }
        for (int page = 0; page < 3; page++) {
            mvc.perform(get("/api/v1/items").param("q", "browse-24-unique").param("page", String.valueOf(page)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(24))
                    .andExpect(jsonPath("$.totalElements").value(49))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.items.length()").value(page == 2 ? 1 : 24));
        }
    }

    @Test
    void thirtyBoosts_eachPageLeadsWithThreeRotatedBoostsAndOtherBoostsStayInRegularOrder() throws Exception {
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long userId = jdbc.sql(
                        "INSERT INTO users (email, password_hash, full_name, phone, status) "
                                + "VALUES ('boost-priority-test@shareloop.local', 'unused', 'Test', '0900000779', 'ACTIVE') RETURNING id")
                .query(Long.class)
                .single();
        for (int number = 0; number < 48; number++) {
            Item entry = item(userId, categoryId, areaId, "Bài boost-priority-unique " + number);
            entry.approve();
            items.saveAndFlush(entry);
            if (number < 30) {
                jdbc.sql("UPDATE items SET boosted_until = CURRENT_TIMESTAMP + INTERVAL '1 day' WHERE id = :id")
                        .param("id", entry.getId())
                        .update();
            }
        }
        // 48 bài, 30 bài đẩy (0..29), cỡ trang mặc định 24 nên 2 trang; rotationSeed = 0.
        // Chỉ 6 bài đẩy đầu vòng (0..5) lên đầu trang, 3 bài mỗi trang. 24 bài đẩy còn lại nằm trong phần bài thường
        // theo thứ tự mới nhất (47 xuống 6), không biến mất.
        mvc.perform(get("/api/v1/items").param("q", "boost-priority-unique").param("rotationSeed", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(24))
                .andExpect(jsonPath("$.items[0].title").value("Bài boost-priority-unique 0"))
                .andExpect(jsonPath("$.items[2].title").value("Bài boost-priority-unique 2"))
                .andExpect(jsonPath("$.items[3].title").value("Bài boost-priority-unique 47"))
                .andExpect(jsonPath("$.items[23].title").value("Bài boost-priority-unique 27"));
        mvc.perform(get("/api/v1/items")
                        .param("q", "boost-priority-unique")
                        .param("page", "1")
                        .param("rotationSeed", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(24))
                .andExpect(jsonPath("$.items[0].title").value("Bài boost-priority-unique 3"))
                .andExpect(jsonPath("$.items[2].title").value("Bài boost-priority-unique 5"))
                .andExpect(jsonPath("$.items[3].title").value("Bài boost-priority-unique 26"))
                .andExpect(jsonPath("$.items[23].title").value("Bài boost-priority-unique 6"));
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
        Item descriptionOnlyMatch = new Item(
                userId,
                otherCategoryId,
                areaId,
                OfferType.GIVE,
                "Giày thể thao",
                "Có áo khoác",
                null,
                ItemCondition.GOOD,
                null,
                null);
        descriptionOnlyMatch.approve();
        items.saveAndFlush(descriptionOnlyMatch);
        Item substringOnlyMatch = item(userId, otherCategoryId, areaId, "Bao đựng đồ");
        substringOnlyMatch.approve();
        items.saveAndFlush(substringOnlyMatch);
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
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/v1/items").param("q", "áo").param("areaId", String.valueOf(areaId)))
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
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items.length()").value(0));

        mvc.perform(get("/api/v1/items").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(percentTitle.getId()));

        mvc.perform(get("/api/v1/items").param("q", "  ").param("areaId", String.valueOf(areaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
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

    private Item approvedItem(
            long userId, long categoryId, long areaId, String title, OfferType offerType, ItemCondition condition) {
        String desiredItem = offerType == OfferType.SWAP ? "Món mong muốn" : null;
        Item item = new Item(userId, categoryId, areaId, offerType, title, "Mô tả", desiredItem, condition, null, null);
        item.approve();
        return items.saveAndFlush(item);
    }

    @Test
    void publicListAndDetailForOthers_hideBoostTimeline() throws Exception {
        // given: một bài đang đẩy có đủ mốc đợt đẩy
        long ownerId = insertUser("timeline-public-test@shareloop.local", "0900000771");
        long itemId = insertBoostedItem(ownerId, "Bài timeline-public-unique");

        // when + then: danh sách công khai và chi tiết bài (khách xem) chỉ có boostedUntil, không có ba mốc đợt đẩy
        mvc.perform(get("/api/v1/items").param("q", "timeline-public-unique"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].boostedUntil").isNotEmpty())
                .andExpect(jsonPath("$.items[0].boostStartedAt").doesNotExist())
                .andExpect(jsonPath("$.items[0].boostChainEndsAt").doesNotExist())
                .andExpect(jsonPath("$.items[0].boostCooldownUntil").doesNotExist());
        mvc.perform(get("/api/v1/items/{id}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boostedUntil").isNotEmpty())
                .andExpect(jsonPath("$.boostStartedAt").doesNotExist())
                .andExpect(jsonPath("$.boostChainEndsAt").doesNotExist())
                .andExpect(jsonPath("$.boostCooldownUntil").doesNotExist());
    }

    @Test
    void myItemsAndOwnDetail_showBoostTimeline() throws Exception {
        // given: chủ bài đăng nhập, bài đang đẩy có đủ mốc đợt đẩy
        long ownerId = insertUser("timeline-owner-test@shareloop.local", "0900000772");
        long itemId = insertBoostedItem(ownerId, "Bài timeline-owner-unique");
        String token = jwtService.issue(ownerId, false);

        // when + then: "Bài của tôi" và chi tiết bài của chính mình trả đủ ba mốc
        mvc.perform(get("/api/v1/me/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].boostStartedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].boostChainEndsAt").isNotEmpty())
                .andExpect(jsonPath("$[0].boostCooldownUntil").isNotEmpty());
        mvc.perform(get("/api/v1/items/{id}", itemId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boostStartedAt").isNotEmpty())
                .andExpect(jsonPath("$.boostChainEndsAt").isNotEmpty())
                .andExpect(jsonPath("$.boostCooldownUntil").isNotEmpty());
    }

    // ---------- xoay vòng bài đẩy ----------

    @Test
    void fortyItemsThirtyBoosts_eachPageLeadsWithThreeBoostsAndPagesCoverEveryItemOnce() throws Exception {
        // given: 40 bài, 30 bài đầu đang đẩy, cỡ trang 20
        long ownerId = insertUser("rotation-forty-test@shareloop.local", "0900000741");
        List<Long> allIds = createItems(ownerId, firstCategoryId(), "Bài rotation-forty-unique", 40, 30);
        List<Long> boostedIds = allIds.subList(0, 30);

        // when: trang 1 theo giờ hiện tại, trang 2 gửi lại rotationSeed mà trang 1 trả về
        String firstPage =
                getBody(get("/api/v1/items").param("q", "rotation-forty-unique").param("size", "20"));
        Number seed = JsonPath.read(firstPage, "$.rotationSeed");
        String secondPage = getBody(get("/api/v1/items")
                .param("q", "rotation-forty-unique")
                .param("size", "20")
                .param("page", "1")
                .param("rotationSeed", String.valueOf(seed)));

        // then: 2 trang, mỗi trang mở đầu bằng đúng 3 bài đẩy, ghép lại đủ 40 bài không trùng (gồm cả 30 bài đẩy)
        Number totalPages = JsonPath.read(firstPage, "$.totalPages");
        assertThat(totalPages.intValue()).isEqualTo(2);
        List<Long> firstIds = idsOf(firstPage);
        List<Long> secondIds = idsOf(secondPage);
        assertThat(boostedIds).containsAll(firstIds.subList(0, 3));
        assertThat(boostedIds).containsAll(secondIds.subList(0, 3));
        // bài thứ 4 của trang 1 là bài thường mới nhất, nên khối bài đẩy đầu trang đúng 3 bài
        assertThat(boostedIds).doesNotContain(firstIds.get(3));
        List<Long> bothPages = new ArrayList<>(firstIds);
        bothPages.addAll(secondIds);
        assertThat(bothPages).hasSize(40).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(allIds);
    }

    @Test
    void sameRotationSeed_returnsSamePageTwice() throws Exception {
        // given: 10 bài, 6 bài đẩy, cỡ trang 4 nên vòng xoay quyết định bài nào lên đầu
        long ownerId = insertUser("rotation-seed-test@shareloop.local", "0900000742");
        createItems(ownerId, firstCategoryId(), "Bài rotation-seed-unique", 10, 6);

        // when: gọi hai lần cùng một rotationSeed
        String first = getBody(get("/api/v1/items")
                .param("q", "rotation-seed-unique")
                .param("size", "4")
                .param("rotationSeed", "12345"));
        String second = getBody(get("/api/v1/items")
                .param("q", "rotation-seed-unique")
                .param("size", "4")
                .param("rotationSeed", "12345"));

        // then
        assertThat(idsOf(second)).isEqualTo(idsOf(first));
    }

    @Test
    void categoryFilter_onlyRotatesBoostsOfThatCategory() throws Exception {
        // given: mỗi danh mục 4 bài đẩy
        long ownerId = insertUser("rotation-category-test@shareloop.local", "0900000743");
        long categoryId = firstCategoryId();
        List<Long> boostedInCategory = createItems(ownerId, categoryId, "Bài rotation-category-unique a", 4, 4);
        createItems(ownerId, secondCategoryId(), "Bài rotation-category-unique b", 4, 4);

        // when
        String boostedOnly = getBody(get("/api/v1/items")
                .param("q", "rotation-category-unique")
                .param("categoryId", String.valueOf(categoryId))
                .param("boosted", "true"));
        String browse = getBody(get("/api/v1/items")
                .param("q", "rotation-category-unique")
                .param("categoryId", String.valueOf(categoryId))
                .param("size", "4"));

        // then: vòng xoay chỉ gồm bài đẩy của danh mục đã lọc
        assertThat(idsOf(boostedOnly)).containsExactlyInAnyOrderElementsOf(boostedInCategory);
        assertThat(boostedInCategory).containsAll(idsOf(browse).subList(0, 3));
    }

    @Test
    void boostedOnly_returnsEveryActiveBoostAndNothingElse() throws Exception {
        // given: 7 bài đang đẩy, 3 bài thường, 1 bài đang đẩy nhưng bị ẩn, 1 bài đã hết đẩy
        long ownerId = insertUser("rotation-only-test@shareloop.local", "0900000744");
        long categoryId = firstCategoryId();
        List<Long> boostedIds = createItems(ownerId, categoryId, "Bài rotation-only-unique", 10, 7)
                .subList(0, 7);
        long hiddenId = createItems(ownerId, categoryId, "Bài rotation-only-unique hidden", 1, 1)
                .get(0);
        jdbc.sql("UPDATE items SET is_active = false WHERE id = :id")
                .param("id", hiddenId)
                .update();
        long endedId = createItems(ownerId, categoryId, "Bài rotation-only-unique ended", 1, 0)
                .get(0);
        jdbc.sql("UPDATE items SET boosted_until = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE id = :id")
                .param("id", endedId)
                .update();

        // when
        String body = getBody(get("/api/v1/items")
                .param("q", "rotation-only-unique")
                .param("boosted", "true")
                .param("size", "50"));

        // then
        Number totalElements = JsonPath.read(body, "$.totalElements");
        assertThat(totalElements.longValue()).isEqualTo(7);
        assertThat(idsOf(body)).containsExactlyInAnyOrderElementsOf(boostedIds);
    }

    // ---------- banner trang chủ ----------

    @Test
    void featured_returnsAtMostFiveItemsAndOneRotationWindow() throws Exception {
        // given: 8 bài đang đẩy (DB có thể còn bài đẩy khác, banner không lọc)
        long ownerId = insertUser("featured-limit-test@shareloop.local", "0900000745");
        createItems(ownerId, firstCategoryId(), "Bài featured-limit-unique", 8, 8);

        // when
        String body = getBody(get("/api/v1/items/featured"));

        // then: mặc định 5 bài; khoảng giữa hai lần đổi nhóm đúng boost_rotate_seconds
        assertThat(idsOf(body)).hasSize(5);
        Instant rotatedAt = Instant.parse(JsonPath.read(body, "$.rotatedAt"));
        Instant nextRotateAt = Instant.parse(JsonPath.read(body, "$.nextRotateAt"));
        assertThat(Duration.between(rotatedAt, nextRotateAt).getSeconds())
                .isEqualTo(configService.getInt(ConfigKey.BOOST_ROTATE_SECONDS));
    }

    @Test
    void featured_whenMoreThanFiveBoosts_differentViewerKeysSeeDifferentGroups() throws Exception {
        // given: 8 bài đang đẩy
        long ownerId = insertUser("featured-viewer-test@shareloop.local", "0900000746");
        createItems(ownerId, firstCategoryId(), "Bài featured-viewer-unique", 8, 8);

        // when: hai khách khác viewerKey
        String firstViewer = getBody(get("/api/v1/items/featured").param("viewerKey", "0"));
        String secondViewer = getBody(get("/api/v1/items/featured").param("viewerKey", "1"));

        // then
        assertThat(idsOf(secondViewer)).isNotEqualTo(idsOf(firstViewer));
    }

    /**
     * Tạo {@code count} bài APPROVED tên "{prefix} {số}" (id tăng dần theo số); {@code boostedCount} bài đầu đang đẩy
     * thêm 1 ngày. Trả id theo thứ tự tạo.
     */
    private List<Long> createItems(long ownerId, long categoryId, String prefix, int count, int boostedCount) {
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        List<Long> ids = new ArrayList<>();
        for (int number = 0; number < count; number++) {
            Item entry = item(ownerId, categoryId, areaId, prefix + " " + number);
            entry.approve();
            items.saveAndFlush(entry);
            if (number < boostedCount) {
                jdbc.sql("UPDATE items SET boosted_until = CURRENT_TIMESTAMP + INTERVAL '1 day' WHERE id = :id")
                        .param("id", entry.getId())
                        .update();
            }
            ids.add(entry.getId());
        }
        return ids;
    }

    private long firstCategoryId() {
        return jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
    }

    private long secondCategoryId() {
        return jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id OFFSET 1 LIMIT 1")
                .query(Long.class)
                .single();
    }

    private String getBody(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    // JsonPath đọc số nhỏ thành Integer, số lớn thành Long, nên đổi qua Number cho chắc.
    private List<Long> idsOf(String body) {
        List<Number> ids = JsonPath.read(body, "$.items[*].id");
        List<Long> result = new ArrayList<>();
        for (Number id : ids) {
            result.add(id.longValue());
        }
        return result;
    }

    private long insertUser(String email, String phone) {
        return jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status) "
                        + "VALUES (:email, 'unused', 'Test', :phone, 'ACTIVE') RETURNING id")
                .param("email", email)
                .param("phone", phone)
                .query(Long.class)
                .single();
    }

    // Bài APPROVED đang đẩy 1 ngày nữa, đợt bắt đầu từ hôm qua.
    private long insertBoostedItem(long ownerId, String title) {
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        Item entry = item(ownerId, categoryId, areaId, title);
        entry.approve();
        items.saveAndFlush(entry);
        jdbc.sql("UPDATE items SET boosted_until = CURRENT_TIMESTAMP + INTERVAL '1 day', "
                        + "boost_started_at = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE id = :id")
                .param("id", entry.getId())
                .update();
        return entry.getId();
    }

    private Item item(long userId, long categoryId, long areaId, String title) {
        return new Item(
                userId, categoryId, areaId, OfferType.GIVE, title, "Mô tả", null, ItemCondition.GOOD, null, null);
    }
}
