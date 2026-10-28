package com.shareloop.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.report.dto.CreateReportRequest;
import com.shareloop.report.dto.ReportResponse;
import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.service.ReportService;
import com.shareloop.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test tự ẩn bài khi đạt ngưỡng report (BR-A03, FR-49, Issue #151) với PostgreSQL thật: chỉ report đang chờ xử lý mới
 * được đếm, ngưỡng lấy từ cấu hình đã seed (3), bài bị ẩn giữ nguyên status và Admin nhận một thông báo.
 */
class ReportAutoHideIT extends IntegrationTest {

    private static final int PHONE_BASE = ThreadLocalRandom.current().nextInt(10_000_000, 90_000_000);
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    ReportService reportService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();
    private Long itemId;

    // Thứ tự xoá: notifications và reports tham chiếu users, reports tham chiếu items.
    @AfterEach
    void cleanUp() {
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
        }
        if (itemId != null) {
            jdbc.update("DELETE FROM reports WHERE item_id = ?", itemId);
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        userIds.clear();
        itemId = null;
    }

    @Test
    void create_dismissedReportsDoNotCount_hidesItemAtThirdPendingReport() {
        long admin = insertUser(true);
        long donor = insertUser(false);
        itemId = insertApprovedItem(donor);

        // Report đầu đã bị Admin bác bỏ: không còn tính vào ngưỡng
        ReportResponse dismissed = reportService.create(insertUser(false), itemReport());
        jdbc.update("UPDATE reports SET status = 'DISMISSED' WHERE id = ?", dismissed.id());

        reportService.create(insertUser(false), itemReport());
        reportService.create(insertUser(false), itemReport());

        // 3 report nhưng chỉ 2 đang chờ xử lý: bài vẫn hiển thị
        assertThat(isActive(itemId)).isTrue();
        assertThat(adminNotifications(admin)).isZero();

        reportService.create(insertUser(false), itemReport());

        // Report đang chờ thứ 3: bài bị ẩn, giữ nguyên status, Admin nhận đúng một thông báo
        assertThat(isActive(itemId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId))
                .isEqualTo("APPROVED");
        assertThat(adminNotifications(admin)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT link FROM notifications WHERE user_id = ? AND type = 'ITEM_AUTO_HIDDEN'",
                        String.class,
                        admin))
                .isEqualTo("/admin/complaints");
        // U04: chủ bài cũng nhận đúng một thông báo, dẫn về trang Hoạt động
        assertThat(adminNotifications(donor)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT link FROM notifications WHERE user_id = ? AND type = 'ITEM_AUTO_HIDDEN'",
                        String.class,
                        donor))
                .isEqualTo("/activities");
    }

    private CreateReportRequest itemReport() {
        return new CreateReportRequest(
                TargetType.ITEM, itemId, null, null, null, ReportReason.PROHIBITED_ITEM, "Bài đăng hàng cấm");
    }

    private boolean isActive(long id) {
        return jdbc.queryForObject("SELECT is_active FROM items WHERE id = ?", Boolean.class, id);
    }

    private int adminNotifications(long adminId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'ITEM_AUTO_HIDDEN'",
                Integer.class,
                adminId);
    }

    private long insertUser(boolean admin) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, is_admin)"
                        + " VALUES (?, 'not-a-real-hash', 'Report IT', ?, 'ACTIVE', ?) RETURNING id",
                Long.class,
                "report-it-" + UUID.randomUUID() + "@test.local",
                "03" + (PHONE_BASE + PHONE_SEQUENCE.incrementAndGet()),
                admin);
        userIds.add(id);
        return id;
    }

    private long insertApprovedItem(long donorId) {
        Long categoryId =
                jdbc.queryForObject("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1", Long.class);
        Long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Report IT', 'Mô tả', 'GOOD', 'APPROVED') RETURNING id",
                Long.class,
                donorId,
                categoryId,
                areaId);
    }
}
