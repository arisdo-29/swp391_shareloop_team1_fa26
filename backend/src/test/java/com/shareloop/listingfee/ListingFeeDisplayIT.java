package com.shareloop.listingfee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shareloop.listingfee.dto.BoostResponse;
import com.shareloop.listingfee.service.BoostService;
import com.shareloop.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /items/{id} trả kèm expireAt và boostedUntil do module listingfee quản lý. Chạy trên PostgreSQL thật
 * (Testcontainers) để kiểm cả native query đọc hai cột. Không @Transactional ở lớp test vì BoostService tự commit; dữ
 * liệu tự tạo được dọn trong @AfterEach.
 */
@AutoConfigureMockMvc
class ListingFeeDisplayIT extends IntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    BoostService boostService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();

    // Thứ tự xoá: credit_ledger và items tham chiếu users, items tham chiếu item_categories nên xoá trước.
    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM credit_ledger WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM items WHERE donor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        for (Long categoryId : createdCategoryIds) {
            jdbc.update("DELETE FROM item_categories WHERE id = ?", categoryId);
        }
        createdUserIds.clear();
        createdCategoryIds.clear();
    }

    @Test
    void getItem_whenApproved_returnsExpireAtAndNullBoostedUntil() throws Exception {
        // given: bài đã duyệt, còn hạn 30 ngày, chưa từng được đẩy
        long ownerId = insertUser(0);
        long itemId = insertApprovedItem(ownerId, insertCategory(), daysFromNow(30));

        // when + then
        mockMvc.perform(get("/api/v1/items/{id}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expireAt").isNotEmpty())
                .andExpect(jsonPath("$.boostedUntil").doesNotExist());
    }

    @Test
    void getItem_afterBoost_returnsBoostedUntilOfBoostResponse() throws Exception {
        // given: bài đã duyệt của chủ bài đủ Credit, rồi chủ bài đẩy bài
        long ownerId = insertUser(20);
        long itemId = insertApprovedItem(ownerId, insertCategory(), daysFromNow(30));
        BoostResponse boostResponse = boostService.boost(itemId, ownerId);

        // when
        String body = mockMvc.perform(get("/api/v1/items/{id}", itemId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then: PostgreSQL chỉ lưu tới micro giây nên so gần đúng 1 mili giây với giá trị BoostResponse trả về
        Instant boostedUntil = Instant.parse(JsonPath.read(body, "$.boostedUntil"));
        assertThat(boostedUntil).isCloseTo(boostResponse.boostedUntil(), within(1, ChronoUnit.MILLIS));
    }

    // ---------- hàm hỗ trợ ----------

    private Instant daysFromNow(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    private long insertUser(long creditBalance) {
        // Email và phone có unique index nên mỗi user một giá trị riêng; đầu số 0987 khác các test khác.
        int sequence = PHONE_SEQUENCE.incrementAndGet();
        String email = "display-it-" + sequence + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0987%06d", sequence);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'not-a-real-hash', 'Display IT', ?, 'ACTIVE', ?, 0) RETURNING id",
                Long.class,
                email,
                phone,
                creditBalance);
        createdUserIds.add(userId);
        return userId;
    }

    // Tự tạo danh mục cho phép đẩy bài thay vì mượn seed, để cờ is_boostable đúng như test cần.
    private long insertCategory() {
        Long categoryId = jdbc.queryForObject(
                "INSERT INTO item_categories (name, is_boostable) VALUES (?, true) RETURNING id",
                Long.class,
                "Display IT " + System.nanoTime());
        createdCategoryIds.add(categoryId);
        return categoryId;
    }

    private long insertApprovedItem(long donorId, long categoryId, Instant expireAt) {
        Long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition,"
                        + " status, is_active, post_fee_paid, fee_state, pending_fee, pending_fee_type, expire_at)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Display IT item', 'Mô tả', 'NEW', 'APPROVED', true, true,"
                        + " 'CHARGED', 0, 'POST', ?) RETURNING id",
                Long.class,
                donorId,
                categoryId,
                areaId,
                Timestamp.from(expireAt));
    }
}
