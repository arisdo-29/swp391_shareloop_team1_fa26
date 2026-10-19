package com.shareloop.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStage;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.repository.ReportRepository;
import com.shareloop.report.service.AdminReportQueryService;
import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminReportQueryIT extends IntegrationTest {
    @Autowired
    ReportRepository reports;

    @Autowired
    AdminReportQueryService service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void queryCombinesFiltersPaginatesAndBreaksTimestampTiesById() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name, phone, status)
                VALUES ('report-query-it@example.test', 'unused-test-hash', 'Report query IT', '0900000118', 'ACTIVE')
                RETURNING id
                """, Long.class);
        var first = save(userId, TargetType.USER);
        var second = save(userId, TargetType.USER);
        var excluded = save(userId, TargetType.ITEM);
        jdbc.update(
                "UPDATE reports SET created_at = '2100-01-01T00:00:00Z' WHERE id IN (?, ?, ?)",
                first.getId(),
                second.getId(),
                excluded.getId());

        var page = service.list(ReportStatus.OPEN, TargetType.USER, 0, 1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().id()).isEqualTo(second.getId());
        assertThat(service.list(ReportStatus.OPEN, TargetType.USER, 1, 1)
                        .items()
                        .getFirst()
                        .id())
                .isEqualTo(first.getId());
        assertThat(service.list(ReportStatus.OPEN, null, 0, 1)
                        .items()
                        .getFirst()
                        .id())
                .isEqualTo(excluded.getId());
        jdbc.update("UPDATE reports SET is_deleted = true WHERE id = ?", second.getId());
        assertThat(service.list(ReportStatus.OPEN, TargetType.USER, 0, 1)
                        .items()
                        .getFirst()
                        .id())
                .isEqualTo(first.getId());
        assertThat(service.list(ReportStatus.RESOLVED, TargetType.USER, 0, 100).items())
                .noneMatch(report -> report.id().equals(first.getId()));
    }

    private Report save(Long userId, TargetType targetType) {
        return reports.saveAndFlush(new Report(
                userId, targetType, null, null, null, ReportReason.OTHER, "Report query IT", ReportStage.BEFORE));
    }
}
