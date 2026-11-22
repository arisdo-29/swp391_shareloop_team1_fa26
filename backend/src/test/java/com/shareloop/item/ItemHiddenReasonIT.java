package com.shareloop.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.service.ItemService;
import com.shareloop.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** B4: chủ bài xem được lý do bị từ chối và lý do bị ẩn theo bốn trường hợp. */
class ItemHiddenReasonIT extends IntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    ItemService itemService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long userId : userIds) {
            jdbc.update(
                    "DELETE FROM activity_logs WHERE target_type = 'ITEM' AND target_id IN"
                            + " (SELECT id FROM items WHERE donor_id = ?)",
                    userId);
            jdbc.update("DELETE FROM reports WHERE item_id IN (SELECT id FROM items WHERE donor_id = ?)", userId);
            jdbc.update("DELETE FROM items WHERE donor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        userIds.clear();
    }

    @Test
    void listMyItems_whenRejected_returnsRejectionReasonToOwner() {
        // given
        long userId = insertUser();
        long itemId = insertItem(userId, "REJECTED");
        jdbc.update("UPDATE items SET reject_reason = 'Ảnh bị mờ' WHERE id = ?", itemId);

        // when
        ItemResponse response = findMine(userId, itemId);

        // then
        assertThat(response.rejectionReason()).isEqualTo("Ảnh bị mờ");
        assertThat(response.hidden()).isFalse();
    }

    @Test
    void listMyItems_whenAdminHidden_returnsAdminReasonAndNextStep() {
        // given
        long userId = insertUser();
        long itemId = insertItem(userId, "APPROVED");
        jdbc.update("UPDATE items SET is_active = false, hidden_by_admin = true WHERE id = ?", itemId);
        jdbc.update(
                "INSERT INTO activity_logs (type, target_type, target_id, reason)"
                        + " VALUES ('ADMIN_ITEM_HIDDEN', 'ITEM', ?, 'Ảnh có thông tin cá nhân')",
                itemId);

        // when
        ItemResponse response = findMine(userId, itemId);

        // then
        assertThat(response.hidden()).isTrue();
        assertThat(response.hiddenReason()).contains("Admin đã ẩn bài này").contains("Ảnh có thông tin cá nhân");
    }

    @Test
    void listMyItems_whenViolationConcluded_tellsOwnerToEditAndResubmit() {
        // given: bài bị ẩn và có báo cáo đã được chấp thuận
        long userId = insertUser();
        long itemId = insertItem(userId, "APPROVED");
        jdbc.update("UPDATE items SET is_active = false WHERE id = ?", itemId);
        insertReport(userId, itemId, "RESOLVED");

        // when
        ItemResponse response = findMine(userId, itemId);

        // then
        assertThat(response.hiddenReason()).isEqualTo("Bài vi phạm quy định. Sửa bài để gửi duyệt lại.");
    }

    @Test
    void listMyItems_whenReportsPending_saysAdminIsReviewingReports() {
        // given: bài tự ẩn vì đủ báo cáo, chưa có kết luận
        long userId = insertUser();
        long itemId = insertItem(userId, "APPROVED");
        jdbc.update("UPDATE items SET is_active = false WHERE id = ?", itemId);
        insertReport(userId, itemId, "OPEN");

        // when
        ItemResponse response = findMine(userId, itemId);

        // then
        assertThat(response.hiddenReason()).isEqualTo("Bài đang tạm ẩn để Admin xem xét báo cáo.");
    }

    @Test
    void listMyItems_whenDonorLockedAndAdminHidden_lockedReasonComesFirst() {
        // given: trùng hai nguyên nhân thì ưu tiên tài khoản bị khoá
        long userId = insertUser();
        long itemId = insertItem(userId, "APPROVED");
        jdbc.update("UPDATE items SET is_active = false, hidden_by_admin = true WHERE id = ?", itemId);
        jdbc.update("UPDATE users SET is_active = false WHERE id = ?", userId);

        // when
        ItemResponse response = findMine(userId, itemId);

        // then
        assertThat(response.hiddenReason()).contains("tài khoản của bạn đang bị khoá");
    }

    @Test
    void listMyItems_whenItemVisible_hasNoHiddenReason() {
        // given
        long userId = insertUser();
        long itemId = insertItem(userId, "APPROVED");

        // when
        ItemResponse response = findMine(userId, itemId);

        // then
        assertThat(response.hidden()).isFalse();
        assertThat(response.hiddenReason()).isNull();
        assertThat(response.rejectionReason()).isNull();
    }

    private ItemResponse findMine(long userId, long itemId) {
        return itemService.listMyItems(userId).stream()
                .filter(response -> response.id() == itemId)
                .findFirst()
                .orElseThrow();
    }

    private long insertUser() {
        int sequence = SEQUENCE.incrementAndGet();
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status)"
                        + " VALUES (?, 'not-a-real-hash', 'Hidden IT', ?, 'ACTIVE') RETURNING id",
                Long.class,
                "hidden-it-" + sequence + "-" + System.nanoTime() + "@test.local",
                String.format("0996%06d", sequence));
        userIds.add(userId);
        return userId;
    }

    private long insertItem(long userId, String status) {
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition,"
                        + " status, approved_at, fee_state)"
                        + " VALUES (?, (SELECT id FROM item_categories ORDER BY id LIMIT 1),"
                        + " (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1), 'GIVE', 'Bài kiểm lý do ẩn',"
                        + " 'Mô tả', 'NEW', ?, now() - interval '1 day', 'CHARGED') RETURNING id",
                Long.class,
                userId,
                status);
    }

    private void insertReport(long ownerId, long itemId, String status) {
        long reporterId = insertUser();
        jdbc.update(
                "INSERT INTO reports (reporter_id, target_type, item_id, target_user_id, reason, description, stage,"
                        + " status, resolved_at) VALUES (?, 'ITEM', ?, ?, 'PROHIBITED_ITEM', 'Hàng cấm', 'BEFORE', ?,"
                        + " CASE WHEN ? = 'RESOLVED' THEN now() ELSE NULL END)",
                reporterId,
                itemId,
                ownerId,
                status,
                status);
    }
}
