package com.shareloop.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.admin.dto.AnomalyReportResponse;
import com.shareloop.admin.dto.AnomalyUser;
import com.shareloop.admin.dto.RepeatedPairAnomaly;
import com.shareloop.admin.service.AdminAnomalyService;
import com.shareloop.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test cảnh báo cặp giao dịch lặp (FR-113, BR-U08, Issue #148) với PostgreSQL thật: câu gom nhóm theo cặp, cửa sổ và
 * ngưỡng lấy từ cấu hình đã seed (hơn 5 giao dịch trong 90 ngày). Dữ liệu khác trong DB có thể sinh thêm cặp, nên
 * test chỉ xét các cặp của người dùng do chính test tạo.
 */
class AdminAnomalyIT extends IntegrationTest {

    private static final int PHONE_BASE = ThreadLocalRandom.current().nextInt(10_000_000, 90_000_000);
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    AdminAnomalyService adminAnomalyService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> itemIds = new ArrayList<>();
    private final List<Long> requestIds = new ArrayList<>();

    // Thứ tự xoá: requests tham chiếu items và users, items tham chiếu users.
    @AfterEach
    void cleanUp() {
        for (Long requestId : requestIds) {
            jdbc.update("DELETE FROM requests WHERE id = ?", requestId);
        }
        for (Long itemId : itemIds) {
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        requestIds.clear();
        itemIds.clear();
        userIds.clear();
    }

    @Test
    void getAnomalies_flagsPairAboveThresholdCountingBothDirectionsWithinWindow() {
        long a = insertUser("Anomaly IT A");
        long b = insertUser("Anomaly IT B");
        long c = insertUser("Anomaly IT C");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // A–B: 3 lần A cho B, 3 lần B cho A trong 90 ngày = 6 giao dịch, hơn ngưỡng 5
        for (int day = 1; day <= 3; day++) {
            insertRequest(a, b, "COMPLETED", now.minus(Duration.ofDays(day)));
            insertRequest(b, a, "COMPLETED", now.minus(Duration.ofDays(day + 3)));
        }
        // Không tính: hoàn tất ngoài cửa sổ 90 ngày, và giao dịch đã huỷ
        insertRequest(a, b, "COMPLETED", now.minus(Duration.ofDays(100)));
        insertRequest(a, b, "CANCELLED", null);
        // A–C: đúng 5 giao dịch, bằng ngưỡng nên chưa cảnh báo
        for (int day = 1; day <= 5; day++) {
            insertRequest(a, c, "COMPLETED", now.minus(Duration.ofDays(day)));
        }

        AnomalyReportResponse response = adminAnomalyService.getAnomalies();

        assertThat(response.pairWindowDays()).isEqualTo(90);
        assertThat(response.pairThreshold()).isEqualTo(5);
        Set<Long> ours = Set.of(a, b, c);
        List<RepeatedPairAnomaly> ourPairs = response.repeatedPairs().stream()
                .filter(pair -> ours.contains(pair.userA().id())
                        || ours.contains(pair.userB().id()))
                .toList();
        assertThat(ourPairs)
                .containsExactly(new RepeatedPairAnomaly(
                        anomalyUser(Math.min(a, b)), anomalyUser(Math.max(a, b)), 6, now.minus(Duration.ofDays(1))));
    }

    private AnomalyUser anomalyUser(long userId) {
        return jdbc.queryForObject(
                "SELECT id, full_name, email FROM users WHERE id = ?",
                (rs, rowNum) -> new AnomalyUser(rs.getLong("id"), rs.getString("full_name"), rs.getString("email")),
                userId);
    }

    private long insertUser(String fullName) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status)"
                        + " VALUES (?, 'not-a-real-hash', ?, ?, 'ACTIVE') RETURNING id",
                Long.class,
                "anomaly-it-" + UUID.randomUUID() + "@test.local",
                fullName,
                "03" + (PHONE_BASE + PHONE_SEQUENCE.incrementAndGet()));
        userIds.add(id);
        return id;
    }

    private void insertRequest(long donorId, long receiverId, String status, Instant completedAt) {
        Long categoryId =
                jdbc.queryForObject("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1", Long.class);
        Long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        Long itemId = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Anomaly IT', 'Mô tả', 'GOOD') RETURNING id",
                Long.class,
                donorId,
                categoryId,
                areaId);
        itemIds.add(itemId);
        Long requestId = jdbc.queryForObject(
                "INSERT INTO requests (item_id, receiver_id, type, status, completed_at)"
                        + " VALUES (?, ?, 'GIVE', ?, ?) RETURNING id",
                Long.class,
                itemId,
                receiverId,
                status,
                completedAt != null ? Timestamp.from(completedAt) : null);
        requestIds.add(requestId);
    }
}
