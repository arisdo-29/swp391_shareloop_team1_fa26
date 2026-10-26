package com.shareloop.user.seed;

import com.shareloop.item.PostLimitProperties;
import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
import com.shareloop.user.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Tạo sẵn các tài khoản test (không cần OTP) ở dev và staging. Danh sách email lấy từ
 * shareloop.post-limit-bypass-emails để luôn khớp với danh sách được bỏ qua giới hạn đăng bài.
 */
// Chỉ tạo bean khi enabled=true. Không dùng @Profile vì staging chạy profile prod.
@Component
@ConditionalOnProperty(name = "shareloop.seed-test-accounts.enabled", havingValue = "true")
public class TestAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TestAccountSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PostLimitProperties postLimitProperties;
    private final String password;

    public TestAccountSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PostLimitProperties postLimitProperties,
            @Value("${shareloop.seed-test-accounts.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.postLimitProperties = postLimitProperties;
        this.password = password;
    }

    // ApplicationRunner chạy một lần sau khi ứng dụng khởi động xong
    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            log.warn("Seed tài khoản test đã bật nhưng thiếu shareloop.seed-test-accounts.password, bỏ qua.");
            return;
        }
        List<String> emails = postLimitProperties.getPostLimitBypassEmails();
        for (int index = 0; index < emails.size(); index++) {
            seedAccount(emails.get(index), index + 1);
        }
    }

    private void seedAccount(String rawEmail, int number) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(email).isPresent()) {
            log.info("Tài khoản test đã có sẵn, bỏ qua: {}", email);
            return;
        }
        // Số điện thoại giả chỉ để test (cột phone bắt buộc và không được trùng giữa các tài khoản ACTIVE)
        String fakePhone = String.format("090900%04d", number);
        User user = new User(
                email,
                passwordEncoder.encode(password),
                "Tester " + number,
                fakePhone,
                false,
                UserStatus.PENDING_VERIFICATION);
        // Bỏ qua bước OTP: đánh dấu đã xác minh (status ACTIVE). trust_stars, ví... lấy mặc định của DB
        user.markVerified();
        userRepository.save(user);
        log.info("Đã tạo tài khoản test: {}", email);
    }
}
