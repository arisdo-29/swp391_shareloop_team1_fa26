package com.shareloop.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.support.IntegrationTest;
import com.shareloop.user.service.AdminUserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * L6 trên PostgreSQL thật: mở khoá chỉ hiện lại bài ẩn lúc khoá nếu bài không bị báo cáo giữ ẩn. @Transactional: mỗi
 * test rollback nên không cần tự dọn.
 */
@Transactional
class UserUnlockReportedItemsIT extends IntegrationTest {

    @Autowired
    AdminUserService adminUserService;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("Mở khoá giữ ẩn bài đã bị chấp thuận báo cáo và bài còn đủ ngưỡng báo cáo chờ, hiện lại bài còn lại")
    void unlock_keepsItemsHiddenWhenReportsRequireIt() {
        // given: chủ bài có 3 bài đang hiển thị
        long admin = user();
        long donor = user();
        long upheldItem = approvedItem(donor);
        long reportedItem = approvedItem(donor);
        long cleanItem = approvedItem(donor);
        adminUserService.lock(admin, donor, "Vi phạm");
        assertThat(isActive(upheldItem)).isFalse();

        // và trong lúc khoá: một bài bị chấp thuận báo cáo, một bài nhận đủ 3 báo cáo đang chờ (ngưỡng mặc định)
        report(user(), upheldItem, "RESOLVED");
        for (int i = 0; i < 3; i++) {
            report(user(), reportedItem, "OPEN");
        }

        // when
        adminUserService.unlock(admin, donor, "Đã giải trình");

        // then: hai bài vi phạm vẫn ẩn, chỉ bài sạch hiện lại; nhật ký ghi rõ bài nào vẫn ẩn
        assertThat(isActive(upheldItem)).isFalse();
        assertThat(isActive(reportedItem)).isFalse();
        assertThat(isActive(cleanItem)).isTrue();
        entityManager.flush();
        assertThat(jdbc.queryForObject(
                        "SELECT data::text FROM activity_logs WHERE type = 'USER_UNLOCK' AND target_id = ?",
                        String.class,
                        donor))
                .contains("\"restoredItems\": 1")
                .contains(String.valueOf(upheldItem))
                .contains(String.valueOf(reportedItem));
    }

    @Test
    @DisplayName("Bài chỉ có báo cáo đã bác bỏ hoặc dưới ngưỡng thì vẫn được hiện lại khi mở khoá")
    void unlock_restoresItemWithDismissedOrFewPendingReports() {
        // given
        long admin = user();
        long donor = user();
        long item = approvedItem(donor);
        adminUserService.lock(admin, donor, "Vi phạm");
        report(user(), item, "DISMISSED");
        report(user(), item, "OPEN");
        report(user(), item, "OPEN");

        // when
        adminUserService.unlock(admin, donor, "Đã giải trình");

        // then: 2 báo cáo chờ dưới ngưỡng 3, báo cáo bác bỏ không tính
        assertThat(isActive(item)).isTrue();
    }

    private long user() {
        return jdbc.queryForObject(
                """
                INSERT INTO users (email, password_hash, full_name, phone, status)
                VALUES (?, 'unused-test-hash', 'Unlock IT', ?, 'ACTIVE') RETURNING id
                """,
                Long.class,
                UUID.randomUUID() + "@example.test",
                UUID.randomUUID().toString().substring(0, 12));
    }

    private long approvedItem(long donor) {
        return jdbc.queryForObject("""
                INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status)
                VALUES (?, (SELECT id FROM item_categories ORDER BY id LIMIT 1),
                        (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1),
                        'GIVE', 'Unlock IT item', 'Mô tả', 'GOOD', 'APPROVED') RETURNING id
                """, Long.class, donor);
    }

    private void report(long reporter, long item, String status) {
        jdbc.update("""
                INSERT INTO reports (reporter_id, target_type, item_id, reason, description, stage, status)
                VALUES (?, 'ITEM', ?, 'OTHER', 'Báo cáo IT', 'BEFORE', ?)
                """, reporter, item, status);
    }

    // Ẩn và hiện bài đi qua JPA: flush trước để JDBC đọc thấy thay đổi trong cùng transaction
    private boolean isActive(long item) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT is_active FROM items WHERE id = ?", Boolean.class, item);
    }
}
