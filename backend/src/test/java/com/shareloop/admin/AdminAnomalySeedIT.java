package com.shareloop.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.admin.dto.AnomalyReportResponse;
import com.shareloop.admin.dto.RepeatedPairAnomaly;
import com.shareloop.admin.service.AdminAnomalyService;
import com.shareloop.support.IntegrationTest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Kiểm thử script seed dữ liệu demo cảnh báo bất thường: cặp giao dịch lặp (Q04, FR-113, BR-U08).
 * Đảm bảo script db/dev-data/seed_anomaly_pairs.sql chạy thành công trên PostgreSQL thật
 * và sinh ra cảnh báo cặp giao dịch lặp với số giao dịch hoàn tất >= 6.
 */
class AdminAnomalySeedIT extends IntegrationTest {

    @Autowired
    AdminAnomalyService adminAnomalyService;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM requests WHERE item_id IN (SELECT id FROM items WHERE title LIKE '%Demo lặp%')");
        jdbc.update("DELETE FROM items WHERE title LIKE '%Demo lặp%'");
        jdbc.update("DELETE FROM users WHERE email IN ('an@shareloop.local', 'binh@shareloop.local')");
    }

    @Test
    void seedAnomalyScript_createsRepeatedPairsForDemo() throws SQLException {
        try (Connection conn = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/dev-data/seed_anomaly_pairs.sql"));
        }

        AnomalyReportResponse report = adminAnomalyService.getAnomalies();

        assertThat(report.pairThreshold()).isEqualTo(5);
        assertThat(report.pairWindowDays()).isEqualTo(90);

        RepeatedPairAnomaly demoPair = report.repeatedPairs().stream()
                .filter(pair -> "an@shareloop.local".equals(pair.userA().email())
                        || "binh@shareloop.local".equals(pair.userA().email())
                        || "an@shareloop.local".equals(pair.userB().email())
                        || "binh@shareloop.local".equals(pair.userB().email()))
                .findFirst()
                .orElse(null);

        assertThat(demoPair).isNotNull();
        assertThat(demoPair.completedCount()).isGreaterThanOrEqualTo(6);
        assertThat(demoPair.lastCompletedAt()).isNotNull();
    }
}
