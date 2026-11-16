package com.shareloop.demo;

import com.shareloop.integration.storage.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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
 * 1 người còn đúng 1 sao uy tín, 1 giao dịch đã chốt lịch (đã hiện liên lạc), mỗi bài demo 3 ảnh minh hoạ. Chạy lại
 * bao nhiêu lần cũng không lỗi trùng dữ liệu.
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
    private final StorageService storageService;
    private final String password;

    // Tiêu đề bài demo và tên file ảnh minh hoạ trong db/demo/images (mỗi bài 3 ảnh, đủ tối thiểu BD-01).
    private static final String[][] DEMO_ITEM_PHOTOS = {
        {"Demo - Giáo trình Java cơ bản", "giao-trinh-java"},
        {"Demo - Vợt cầu lông", "vot-cau-long"},
        {"Demo - Nồi cơm điện nhỏ", "noi-com-dien"},
        {"Demo - Bộ ly thủy tinh (đã chốt lịch)", "bo-ly-thuy-tinh"},
    };
    private static final int PHOTOS_PER_ITEM = 3;

    public DemoDataSeeder(
            JdbcTemplate jdbcTemplate,
            DataSource dataSource,
            PasswordEncoder passwordEncoder,
            StorageService storageService,
            // Mật khẩu TEST của tài khoản demo; không dùng lại mật khẩu thật. Đổi bằng biến môi trường
            // DEMO_SEED_PASSWORD.
            @Value("${DEMO_SEED_PASSWORD:Demo@12345}") String password) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.passwordEncoder = passwordEncoder;
        this.storageService = storageService;
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
        attachDemoPhotos();
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

    // Gắn 3 ảnh minh hoạ cho mỗi bài demo chưa có ảnh. Ảnh lưu qua StorageService (local hoặc R2 tuỳ môi trường),
    // giống ảnh người dùng tải lên. Bài đã có ảnh thì bỏ qua nên chạy lại không tạo trùng.
    private void attachDemoPhotos() {
        for (String[] demoItem : DEMO_ITEM_PHOTOS) {
            List<Long> itemIds = jdbcTemplate.queryForList(
                    "SELECT i.id FROM items i JOIN users u ON u.id = i.donor_id"
                            + " WHERE i.title = ? AND u.email LIKE '%.demo@shareloop.local' AND NOT i.is_deleted"
                            + " AND NOT EXISTS (SELECT 1 FROM item_media_mappings m WHERE m.item_id = i.id)",
                    Long.class, demoItem[0]);
            for (Long itemId : itemIds) {
                for (int index = 1; index <= PHOTOS_PER_ITEM; index++) {
                    attachPhoto(itemId, demoItem[1] + "-" + index + ".jpg", index - 1);
                }
            }
        }
    }

    private void attachPhoto(long itemId, String fileName, int sortOrder) {
        byte[] bytes;
        try (InputStream input = new ClassPathResource("db/demo/images/" + fileName).getInputStream()) {
            bytes = input.readAllBytes();
        } catch (IOException e) {
            log.warn("Seed demo: không đọc được ảnh {}", fileName, e);
            return;
        }
        String storageKey;
        try {
            storageKey = storageService.save(bytes, "jpg");
        } catch (IOException e) {
            log.warn("Seed demo: không lưu được ảnh {}", fileName, e);
            return;
        }
        Long mediaId = jdbcTemplate.queryForObject(
                "INSERT INTO media_files (storage_key, mime_type, size_bytes, width, height, visibility, status)"
                        + " VALUES (?, 'image/jpeg', ?, 800, 800, 'PUBLIC', 'ATTACHED') RETURNING id",
                Long.class,
                storageKey,
                bytes.length);
        jdbcTemplate.update(
                "INSERT INTO item_media_mappings (item_id, media_id, role, sort_order) VALUES (?, ?, 'PHOTO', ?)",
                itemId,
                mediaId,
                sortOrder);
    }
}
