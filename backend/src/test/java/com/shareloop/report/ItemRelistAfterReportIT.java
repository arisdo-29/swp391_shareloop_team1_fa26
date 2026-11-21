package com.shareloop.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.item.service.ItemService;
import com.shareloop.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chạy thật trên PostgreSQL: bài bị ẩn vì báo cáo vi phạm được chấp nhận chỉ hiện lại khi chủ bài sửa (PUT
 * /items/{id}) và Admin duyệt lại. Chủ bài chưa sửa, hoặc bài ẩn vì lý do khác, thì vẫn ẩn.
 */
@AutoConfigureMockMvc
@Transactional
class ItemRelistAfterReportIT extends IntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EntityManager entityManager;

    @Autowired
    ItemService itemService;

    @Test
    void approveAgain_whenHiddenByUpheldReportAndOwnerEdited_relistsItem() throws Exception {
        // given: bài đã duyệt hôm qua, báo cáo được chấp nhận sau đó nên bài bị ẩn, chủ bài rồi sửa bài
        long ownerId = user("relist-owner-1@shareloop.local", "0900000801");
        long reporterId = user("relist-reporter-1@shareloop.local", "0900000802");
        long itemId = hiddenItemWithUpheldReport(ownerId, reporterId);
        editItem(ownerId, itemId);

        // when
        itemService.markApproved(itemId);
        entityManager.flush();

        // then
        assertThat(statusOf(itemId)).isEqualTo("APPROVED");
        assertThat(activeOf(itemId)).isTrue();
    }

    @Test
    void ownerHasNotEdited_itemStaysHidden() {
        // given: báo cáo được chấp nhận, chủ bài chưa sửa nên bài chưa qua duyệt lại
        long ownerId = user("relist-owner-2@shareloop.local", "0900000803");
        long reporterId = user("relist-reporter-2@shareloop.local", "0900000804");

        // when
        long itemId = hiddenItemWithUpheldReport(ownerId, reporterId);
        entityManager.flush();

        // then
        assertThat(statusOf(itemId)).isEqualTo("APPROVED");
        assertThat(activeOf(itemId)).isFalse();
    }

    @Test
    void approveAgain_whenHiddenForOtherReason_keepsItemHidden() throws Exception {
        // given: bài bị ẩn nhưng không có báo cáo nào được chấp nhận (ví dụ Admin gỡ thủ công)
        long ownerId = user("relist-owner-3@shareloop.local", "0900000805");
        long itemId = approvedItem(ownerId);
        jdbc.sql("UPDATE items SET is_active = false WHERE id = :id")
                .param("id", itemId)
                .update();
        entityManager.clear();
        editItem(ownerId, itemId);

        // when
        itemService.markApproved(itemId);
        entityManager.flush();

        // then
        assertThat(activeOf(itemId)).isFalse();
    }

    @Test
    void approveAgain_whenUpheldReportOlderThanLastApproval_doesNotRelist() {
        // given: báo cáo được chấp nhận từ trước lần duyệt gần nhất (bài đã hiện lại ở đó), sau đó bài bị ẩn vì lý do
        // khác
        long ownerId = user("relist-owner-4@shareloop.local", "0900000806");
        long reporterId = user("relist-reporter-4@shareloop.local", "0900000807");
        long itemId = approvedItem(ownerId);
        insertUpheldReport(itemId, ownerId, reporterId, "now() - interval '3 days'");
        jdbc.sql("UPDATE items SET is_active = false, status = 'PENDING_REVIEW' WHERE id = :id")
                .param("id", itemId)
                .update();
        entityManager.clear();

        // when
        itemService.markApproved(itemId);
        entityManager.flush();

        // then
        assertThat(activeOf(itemId)).isFalse();
    }

    // ---------- Helper ----------

    private long hiddenItemWithUpheldReport(long ownerId, long reporterId) {
        long itemId = approvedItem(ownerId);
        insertUpheldReport(itemId, ownerId, reporterId, "now() - interval '1 hour'");
        jdbc.sql("UPDATE items SET is_active = false WHERE id = :id")
                .param("id", itemId)
                .update();
        entityManager.clear();
        return itemId;
    }

    private void insertUpheldReport(long itemId, long ownerId, long reporterId, String resolvedAtSql) {
        jdbc.sql("INSERT INTO reports (reporter_id, target_type, item_id, target_user_id, reason, description, stage, "
                        + "status, stars_deducted, resolved_at) VALUES (:reporterId, 'ITEM', :itemId, :ownerId, "
                        + "'COUNTERFEIT', 'Hàng giả', 'BEFORE', 'RESOLVED', 1, " + resolvedAtSql + ")")
                .param("reporterId", reporterId)
                .param("itemId", itemId)
                .param("ownerId", ownerId)
                .update();
    }

    private void editItem(long ownerId, long itemId) throws Exception {
        mvc.perform(put("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(ownerId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"areaId\":2,\"offerType\":\"GIVE\",\"title\":\"Áo khoác sửa\","
                                + "\"description\":\"Đã sửa mô tả\",\"condition\":\"GOOD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        entityManager.flush();
        entityManager.clear();
    }

    private String statusOf(long itemId) {
        return jdbc.sql("SELECT status FROM items WHERE id = :id")
                .param("id", itemId)
                .query(String.class)
                .single();
    }

    private boolean activeOf(long itemId) {
        return jdbc.sql("SELECT is_active FROM items WHERE id = :id")
                .param("id", itemId)
                .query(Boolean.class)
                .single();
    }

    private long user(String email, String phone) {
        return jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance) "
                        + "VALUES (:email, 'unused', 'Test', :phone, 'ACTIVE', 20) RETURNING id")
                .param("email", email)
                .param("phone", phone)
                .query(Long.class)
                .single();
    }

    private long approvedItem(long ownerId) {
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        return jdbc.sql("INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, "
                        + "status, approved_at, post_fee_paid, fee_state, free_edit_used) "
                        + "VALUES (:ownerId, :categoryId, :areaId, 'GIVE', 'Tên cũ', 'Mô tả cũ', 'GOOD', "
                        + "'APPROVED', now() - interval '1 day', false, 'CHARGED', true) RETURNING id")
                .param("ownerId", ownerId)
                .param("categoryId", categoryId)
                .param("areaId", areaId)
                .query(Long.class)
                .single();
    }
}
