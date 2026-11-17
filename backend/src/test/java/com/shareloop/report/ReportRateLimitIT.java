package com.shareloop.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shareloop.common.exception.BusinessException;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Kiểm thử tích hợp luật giới hạn báo cáo mới (BC-01) với cơ sở dữ liệu thật:
 * 1. Cùng một chủ bài: A báo cáo tối đa 3 bài khác nhau của B trong 30 ngày; bài thứ 4 bị chặn REPORT_OWNER_LIMIT_REACHED.
 * 2. Báo cáo người dùng B: cho báo cáo lại sau 30 ngày thay vì chặn vĩnh viễn.
 */
class ReportRateLimitIT extends IntegrationTest {

    private static final int PHONE_BASE = ThreadLocalRandom.current().nextInt(10_000_000, 90_000_000);
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    ReportService reportService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> itemIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long itemId : itemIds) {
            jdbc.update("DELETE FROM reports WHERE item_id = ?", itemId);
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        itemIds.clear();

        for (Long userId : userIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM reports WHERE reporter_id = ? OR target_user_id = ?", userId, userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        userIds.clear();
    }

    @Test
    @DisplayName("BC-01: A báo cáo tối đa 3 bài của cùng chủ B trong 30 ngày; bài thứ 4 ném REPORT_OWNER_LIMIT_REACHED")
    void reportItem_max3ItemsPerOwnerIn30Days() {
        long reporterA = insertUser();
        long donorB = insertUser();

        long item1 = insertApprovedItem(donorB);
        long item2 = insertApprovedItem(donorB);
        long item3 = insertApprovedItem(donorB);
        long item4 = insertApprovedItem(donorB);

        // Báo cáo 3 bài đầu tiên thành công
        ReportResponse r1 = reportService.create(reporterA, itemReport(item1));
        ReportResponse r2 = reportService.create(reporterA, itemReport(item2));
        ReportResponse r3 = reportService.create(reporterA, itemReport(item3));

        assertThat(r1).isNotNull();
        assertThat(r2).isNotNull();
        assertThat(r3).isNotNull();

        // Báo cáo bài thứ 4 của cùng donorB bị chặn
        assertThatThrownBy(() -> reportService.create(reporterA, itemReport(item4)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_OWNER_LIMIT_REACHED);
                    assertThat(be.getMessage())
                            .isEqualTo("Bạn đã báo cáo 3 bài của người này trong 30 ngày qua. Vui lòng thử lại sau.");
                });
    }

    @Test
    @DisplayName("BC-01: Báo cáo người dùng B cho phép báo cáo lại sau 30 ngày")
    void reportUser_allowsReportingAgainAfter30Days() {
        long reporterA = insertUser();
        long targetUserB = insertUser();

        CreateReportRequest userReport = new CreateReportRequest(
                TargetType.USER, targetUserB, null, null, null, ReportReason.HARASSMENT, "Quấy rối");

        // Lần 1: Báo cáo thành công
        ReportResponse firstReport = reportService.create(reporterA, userReport);
        assertThat(firstReport).isNotNull();

        // Lần 2 (ngay sau đó): Bị chặn vì đã báo cáo trong vòng 30 ngày
        assertThatThrownBy(() -> reportService.create(reporterA, userReport))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_ALREADY_REPORTED));

        // Giả lập lần báo cáo trước đã qua 31 ngày
        jdbc.update("UPDATE reports SET created_at = NOW() - INTERVAL '31 days' WHERE id = ?", firstReport.id());

        // Lần 3: Sau 30 ngày, cho phép báo cáo lại thành công
        ReportResponse secondReport = reportService.create(reporterA, userReport);
        assertThat(secondReport).isNotNull();
        assertThat(secondReport.id()).isNotEqualTo(firstReport.id());
    }

    private CreateReportRequest itemReport(long itemId) {
        return new CreateReportRequest(
                TargetType.ITEM, itemId, null, null, null, ReportReason.PROHIBITED_ITEM, "Hàng cấm");
    }

    private long insertUser() {
        Long id = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, is_admin)"
                        + " VALUES (?, 'not-a-real-hash', 'Report IT', ?, 'ACTIVE', false) RETURNING id",
                Long.class,
                "report-it-" + UUID.randomUUID() + "@test.local",
                "03" + (PHONE_BASE + PHONE_SEQUENCE.incrementAndGet()));
        userIds.add(id);
        return id;
    }

    private long insertApprovedItem(long donorId) {
        Long categoryId =
                jdbc.queryForObject("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1", Long.class);
        Long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        Long id = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Report IT Item', 'Mô tả', 'GOOD', 'APPROVED') RETURNING id",
                Long.class,
                donorId,
                categoryId,
                areaId);
        itemIds.add(id);
        return id;
    }
}

