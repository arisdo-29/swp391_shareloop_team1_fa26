package com.shareloop.auth.service;

import com.shareloop.auth.AuthErrorCode;
import com.shareloop.auth.dto.AuthResponse;
import com.shareloop.auth.dto.LoginRequest;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
import com.shareloop.user.mapper.UserMapper;
import com.shareloop.user.repository.UserRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true) // đăng nhập chỉ đọc DB: mở transaction read-only
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final long SECONDS_PER_MINUTE = 60;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final long jwtTtlMinutes;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserMapper userMapper,
            @Value("${app.jwt.ttl-minutes}") long jwtTtlMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.jwtTtlMinutes = jwtTtlMinutes;
    }

    /**
     * Đăng nhập bằng email và mật khẩu (FR-03, UC-02).
     *
     * <p>Quy tắc: sai email và sai mật khẩu cùng trả một lỗi để không dò được email; chỉ khi mật khẩu đúng mới
     * kiểm trạng thái tài khoản.
     *
     * @throws BusinessException AUTH_INVALID_CREDENTIALS, AUTH_EMAIL_NOT_VERIFIED, AUTH_ACCOUNT_LOCKED
     */
    public AuthResponse login(LoginRequest request) {
        User user = findUserWithMatchingPassword(request);
        checkAccountCanLogin(user);
        return buildAuthResponse(user);
    }

    private User findUserWithMatchingPassword(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        return user;
    }

    private void checkAccountCanLogin(User user) {
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new BusinessException(AuthErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }
        // Khoá tài khoản là is_active = false (status vẫn giữ nguyên), nên phải kiểm cả hai.
        if (user.getStatus() == UserStatus.BANNED || !user.isActive()) {
            throw new BusinessException(AuthErrorCode.AUTH_ACCOUNT_LOCKED);
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.issue(user.getId(), user.isAdmin());
        long expiresInSeconds = jwtTtlMinutes * SECONDS_PER_MINUTE;
        return new AuthResponse(accessToken, TOKEN_TYPE, expiresInSeconds, userMapper.toAuthUserResponse(user));
    }
}
