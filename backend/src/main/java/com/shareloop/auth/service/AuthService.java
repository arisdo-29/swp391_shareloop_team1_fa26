package com.shareloop.auth.service;

import com.shareloop.auth.AuthErrorCode;
import com.shareloop.auth.dto.AuthResponse;
import com.shareloop.auth.dto.LoginRequest;
import com.shareloop.auth.dto.OtpSentResponse;
import com.shareloop.auth.dto.RegisterRequest;
import com.shareloop.auth.dto.ResendOtpRequest;
import com.shareloop.auth.dto.VerifyOtpRequest;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
import com.shareloop.user.mapper.UserMapper;
import com.shareloop.user.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true) // mặc định chỉ đọc DB; method nào ghi thì tự khai @Transactional riêng
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final long SECONDS_PER_MINUTE = 60;
    private static final String VIETNAM_COUNTRY_PREFIX = "+84";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final OtpService otpService;
    private final ConfigService configService;
    private final long jwtTtlMinutes;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserMapper userMapper,
            OtpService otpService,
            ConfigService configService,
            @Value("${app.jwt.ttl-minutes}") long jwtTtlMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.otpService = otpService;
        this.configService = configService;
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

    /**
     * Đăng ký tài khoản và gửi OTP về email (FR-01, FR-02). Email còn PENDING_VERIFICATION thì cập nhật thông tin
     * rồi cấp OTP mới (vẫn theo cooldown).
     *
     * @throws BusinessException AUTH_EMAIL_ALREADY_REGISTERED, AUTH_PHONE_ALREADY_USED, AUTH_OTP_RESEND_TOO_SOON
     */
    @Transactional // mở transaction ghi: lưu user và OTP cùng lúc, lỗi giữa chừng thì hoàn tác hết
    public OtpSentResponse register(RegisterRequest request, String registrationIp) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        String fullName = request.fullName().trim();
        String passwordHash = passwordEncoder.encode(request.password());

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent() && existingUser.get().getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new BusinessException(AuthErrorCode.AUTH_EMAIL_ALREADY_REGISTERED);
        }
        checkPhoneNotUsedByActiveAccount(phone);

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            user.updatePendingRegistration(passwordHash, fullName, phone);
        } else {
            user = new User(email, passwordHash, fullName, phone, false, UserStatus.PENDING_VERIFICATION);
            user.recordRegistrationIp(registrationIp);
            userRepository.save(user);
        }
        otpService.issue(user);
        return buildOtpSentResponse(email);
    }

    /**
     * Xác thực OTP, kích hoạt tài khoản và trả JWT như đăng nhập (FR-02).
     *
     * @throws BusinessException AUTH_OTP_INVALID, AUTH_OTP_EXPIRED, AUTH_OTP_TOO_MANY_ATTEMPTS,
     *     AUTH_PHONE_ALREADY_USED
     */
    // noRollbackFor: nhập sai mã vẫn phải LƯU việc tăng otp_failed_count / xoá mã. Mặc định Spring rollback khi
    // gặp RuntimeException (BusinessException cũng là RuntimeException) nên bộ đếm sai sẽ không bao giờ tăng.
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        User user = findPendingUser(normalizeEmail(request.email()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_OTP_INVALID));
        otpService.verify(user, request.otp());
        // Trong lúc chờ OTP có thể đã có tài khoản ACTIVE khác giữ số này; user vẫn PENDING để đăng ký lại số khác.
        checkPhoneNotUsedByActiveAccount(user.getPhone());
        user.markVerified();
        return buildAuthResponse(user);
    }

    /**
     * Gửi lại OTP cho tài khoản đang chờ xác thực (FR-02). Email không có hoặc đã ACTIVE vẫn trả cùng nội dung nhưng
     * không gửi email, để không dò được danh sách email.
     *
     * @throws BusinessException AUTH_OTP_RESEND_TOO_SOON
     */
    @Transactional
    public OtpSentResponse resendOtp(ResendOtpRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> pendingUser = findPendingUser(email);
        if (pendingUser.isPresent()) {
            otpService.issue(pendingUser.get());
        }
        return buildOtpSentResponse(email);
    }

    private Optional<User> findPendingUser(String email) {
        return userRepository.findByEmail(email).filter(user -> user.getStatus() == UserStatus.PENDING_VERIFICATION);
    }

    private void checkPhoneNotUsedByActiveAccount(String phone) {
        if (userRepository.existsByPhoneAndStatus(phone, UserStatus.ACTIVE)) {
            throw new BusinessException(AuthErrorCode.AUTH_PHONE_ALREADY_USED);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // +84901234567 -> 0901234567 (số lưu trong DB luôn bắt đầu bằng 0)
    private String normalizePhone(String phone) {
        if (phone.startsWith(VIETNAM_COUNTRY_PREFIX)) {
            return "0" + phone.substring(VIETNAM_COUNTRY_PREFIX.length());
        }
        return phone;
    }

    private OtpSentResponse buildOtpSentResponse(String email) {
        int expiresInSeconds = configService.getInt(ConfigKey.OTP_TTL_MINUTES) * (int) SECONDS_PER_MINUTE;
        int cooldownSeconds = configService.getInt(ConfigKey.OTP_RESEND_COOLDOWN_SECONDS);
        return new OtpSentResponse(email, expiresInSeconds, cooldownSeconds);
    }

    private User findUserWithMatchingPassword(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
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
