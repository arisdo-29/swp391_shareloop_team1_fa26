package com.shareloop.demo;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Dựng dữ liệu demo lặp lại được (task K3): 1 Admin, 3 người dùng thường đã có Credit, bài Cho và Trao đổi đã duyệt,
 * 1 người còn đúng 1 sao uy tín, 1 giao dịch đã chốt lịch. Chạy lại bao nhiêu lần cũng không lỗi trùng dữ liệu.
 *
 * <p>Chỉ tạo bean khi profile "demo" đang bật; mọi profile khác (dev, test, prod) không bao giờ chạy seed này. Đây là
 * dữ liệu nền, không nằm trong migration Flyway.
 */
// @Profile: Spring chỉ tạo bean khi profile có tên này đang bật (SPRING_PROFILES_ACTIVE=...,demo).
@Profile("demo")
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    // Bốn tài khoản: email, họ tên, số điện thoại, là Admin, số sao uy tín. Số điện thoại khác nhóm 0901... của
    // dev-data.
    private static final Object[][] DEMO_USERS = {
        {"admin.demo@shareloop.local", "Quản trị viên Demo", "0902000001", true, 5},
        {"an.demo@shareloop.local", "Nguyễn Văn An (demo)", "0902000002", false, 5},
        {"binh.demo@shareloop.local", "Trần Thị Bình (demo)", "0902000003", false, 5},
        // Còn đúng 1 sao uy tín để demo cấm tự động
        {"chi.demo@shareloop.local", "Lê Thị Chi (demo)", "0902000004", false, 1},
    };

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;
    private final String password;

    public DemoDataSeeder(
            JdbcTemplate jdbcTemplate,
            DataSource dataSource,
            PasswordEncoder passwordEncoder,
            // Mật khẩu TEST của tài khoản demo; không dùng lại mật khẩu thật. Đổi bằng biến môi trường
            // DEMO_SEED_PASSWORD.
            @Value("${DEMO_SEED_PASSWORD:Demo@12345}") String password) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.passwordEncoder = passwordEncoder;
        this.password = password;
    }

    // ApplicationRunner chạy một lần sau khi ứng dụng khởi động xong (Flyway đã tạo schema)
    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            log.warn("Seed demo bị bỏ qua: DEMO_SEED_PASSWORD đang rỗng.");
            return;
        }
        insertUsers();
        runSqlScript();
        log.info("Seed demo xong. Tài khoản: admin.demo / an.demo / binh.demo / chi.demo @shareloop.local");
    }

    // Hash bằng cùng PasswordEncoder (BCrypt) mà AuthService dùng, nên đăng nhập bằng mật khẩu demo hoạt động bình
    // thường.
    private void insertUsers() {
        String passwordHash = passwordEncoder.encode(password);
        for (Object[] user : DEMO_USERS) {
            insertUser(passwordHash, user);
        }
    }

    // ON CONFLICT ... DO NOTHING: email đã có (kể cả lần chạy trước) thì bỏ qua, không lỗi trùng.
    private void insertUser(String passwordHash, Object[] user) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, full_name, phone, is_admin, status, trust_stars)"
                        + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)"
                        + " ON CONFLICT (email) WHERE NOT is_deleted DO NOTHING",
                user[0],
                passwordHash,
                user[1],
                user[2],
                user[3],
                user[4]);
    }

    private void runSqlScript() {
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(false, false, "UTF-8", new ClassPathResource("db/demo/demo-seed.sql"));
        populator.execute(dataSource);
    }
}
