package com.shareloop.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.support.IntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Tự tạo DemoDataSeeder bằng tay trên context test sẵn có (không bật profile demo) để không tạo thêm Spring context:
 * mỗi context thêm một pool kết nối và làm Postgres của bộ IT báo "too many clients". Việc chỉ tạo bean khi có
 * profile demo đã được kiểm ở DemoDataSeederProfileTest.
 */
class DemoDataSeederIT extends IntegrationTest {

    private static final String DEMO_EMAIL_PATTERN = "%.demo@shareloop.local";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Autowired
    PasswordEncoder passwordEncoder;

    DemoDataSeeder demoDataSeeder;

    @BeforeEach
    void seed() {
        demoDataSeeder = new DemoDataSeeder(jdbc, dataSource, passwordEncoder, "Demo@12345");
        demoDataSeeder.run(null);
    }

    // Dọn sạch để không làm lệch số liệu của các IT khác dùng chung database.
    @AfterEach
    void cleanUp() {
        jdbc.update(
                "DELETE FROM requests WHERE item_id IN (SELECT i.id FROM items i JOIN users u ON u.id = i.donor_id WHERE u.email LIKE ?)",
                DEMO_EMAIL_PATTERN);
        jdbc.update(
                "DELETE FROM items WHERE donor_id IN (SELECT id FROM users WHERE email LIKE ?)", DEMO_EMAIL_PATTERN);
        jdbc.update(
                "DELETE FROM credit_ledger WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)",
                DEMO_EMAIL_PATTERN);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", DEMO_EMAIL_PATTERN);
    }

    @Test
    void run_whenCalledAgain_doesNotDuplicateOrChangeData() {
        // given: dữ liệu đã seed ở @BeforeEach

        // when: chạy lại hai lần nữa
        demoDataSeeder.run(null);
        demoDataSeeder.run(null);

        // then: số lượng và số dư giữ nguyên
        assertThat(count("SELECT count(*) FROM users WHERE email LIKE ?")).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM items i JOIN users u ON u.id = i.donor_id WHERE u.email LIKE ?"))
                .isEqualTo(4);
        assertThat(count("SELECT count(*) FROM requests r JOIN users u ON u.id = r.receiver_id WHERE u.email LIKE ?"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM credit_ledger l JOIN users u ON u.id = l.user_id WHERE u.email LIKE ?"))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                        "SELECT credit_balance FROM users WHERE email = 'an.demo@shareloop.local'", Long.class))
                .isEqualTo(200);
    }

    @Test
    void run_createsAdminUsersWithCreditAndOneStarUser() {
        // given: dữ liệu đã seed ở @BeforeEach

        // when
        Boolean adminExists = jdbc.queryForObject(
                "SELECT is_admin FROM users WHERE email = 'admin.demo@shareloop.local'", Boolean.class);
        Integer starsOfChi = jdbc.queryForObject(
                "SELECT trust_stars FROM users WHERE email = 'chi.demo@shareloop.local'", Integer.class);
        Integer ordinaryUsersWithCredit =
                count("SELECT count(*) FROM users WHERE email LIKE ? AND NOT is_admin AND credit_balance > 0");

        // then
        assertThat(adminExists).isTrue();
        assertThat(starsOfChi).isEqualTo(1);
        assertThat(ordinaryUsersWithCredit).isEqualTo(3);
    }

    @Test
    void run_createsApprovedGiveAndSwapItemsAndOneLogisticsConfirmedRequest() {
        // given: dữ liệu đã seed ở @BeforeEach

        // when
        Integer approvedGive = count(
                "SELECT count(*) FROM items i JOIN users u ON u.id = i.donor_id WHERE u.email LIKE ? AND i.status = 'APPROVED' AND i.offer_type = 'GIVE'");
        Integer approvedSwap = count(
                "SELECT count(*) FROM items i JOIN users u ON u.id = i.donor_id WHERE u.email LIKE ? AND i.status = 'APPROVED' AND i.offer_type = 'SWAP'");
        Integer confirmedRequests = count(
                "SELECT count(*) FROM requests r JOIN users u ON u.id = r.receiver_id WHERE u.email LIKE ? AND r.status = 'LOGISTICS_CONFIRMED'");

        // then
        assertThat(approvedGive).isGreaterThanOrEqualTo(1);
        assertThat(approvedSwap).isGreaterThanOrEqualTo(1);
        assertThat(confirmedRequests).isEqualTo(1);
    }

    @Test
    void run_hashesDemoPasswordWithPasswordEncoder() {
        // given: dữ liệu đã seed ở @BeforeEach

        // when
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'an.demo@shareloop.local'", String.class);

        // then: đăng nhập bằng mật khẩu test đúng cách AuthService so khớp
        assertThat(passwordEncoder.matches("Demo@12345", hash)).isTrue();
    }

    private Integer count(String sql) {
        return jdbc.queryForObject(sql, Integer.class, DEMO_EMAIL_PATTERN);
    }
}
