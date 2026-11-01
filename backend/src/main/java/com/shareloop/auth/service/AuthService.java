package com.shareloop.auth.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogQueryService;
import com.shareloop.auth.AuthErrorCode;
import com.shareloop.auth.dto.AuthResponse;
import com.shareloop.auth.dto.ChangePasswordRequest;
import com.shareloop.auth.dto.ForgotPasswordRequest;
import com.shareloop.auth.dto.LoginRequest;
import com.shareloop.auth.dto.OtpSentResponse;
import com.shareloop.auth.dto.RegisterRequest;
import com.shareloop.auth.dto.ResendOtpRequest;
import com.shareloop.auth.dto.ResetPasswordRequest;
import com.shareloop.auth.dto.VerifyOtpRequest;
import com.shareloop.auth.event.PasswordChangedEvent;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.UserErrorCode;
import com.shareloop.user.entity.OtpPurpose;
import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
import com.shareloop.user.mapper.UserMapper;
import com.shareloop.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true) // mặc định chỉ đọc DB; method nào ghi thì tự khai @Transactional riêng
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final long SECONDS_PER_MINUTE = 60;
    private static final String VIETNAM_COUNTRY_PREFIX = "+84";
    // FR-10, C30: cửa sổ đếm tài khoản theo IP là 24 giờ, đã nằm trong chính tên khoá accounts_per_ip_24h.
    private static final long IP_LIMIT_WINDOW_HOURS = 24;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final OtpService otpService;
    private final ConfigService configService;
    private final DisposableEmailDomains disposableEmailDomains;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final ActivityLogQueryService activityLogQueryService;
    private final long jwtTtlMinutes;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserMapper userMapper,
            OtpService otpService,
            ConfigService configService,
            DisposableEmailDomains disposableEmailDomains,
            Clock clock,
            ApplicationEventPublisher eventPublisher,
            ActivityLogQueryService activityLogQueryService,
            @Value("${app.jwt.ttl-minutes}") long jwtTtlMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.otpService = otpService;
        this.configService = configService;
        this.disposableEmailDomains = disposableEmailDomains;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
        this.activityLogQueryService = activityLogQueryService;
        this.jwtTtlMinutes = jwtTtlMinutes;
    }

    /**
     * Đăng nhập bằng email và mật khẩu (FR-03, UC-02).
     *
     * <p>Quy tắc: sai email và sai mật khẩu cùng trả một lỗi để không dò được email; chỉ khi mật khẩu đúng mới
     * kiểm trạng thái tài khoản.
     *
     * @throws BusinessException AUTH_INVALID_CREDENTIALS, AUTH_EMAIL_NOT_VERIFIED, AUTH_ACCOUNT_LOCKED, AUTH_ACCOUNT_BANNED
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
     * <p>Chống tạo nhiều tài khoản (SRS 5.3): email dùng một lần bị chặn (FR-04); tạo tài khoản MỚI thì IP đăng ký
     * phải có ít hơn accounts_per_ip_24h tài khoản trong 24 giờ qua (FR-10). Đăng ký lại email đang PENDING không tạo
     * tài khoản mới nên không xét IP.
     *
     * @throws BusinessException AUTH_EMAIL_DOMAIN_NOT_ALLOWED, AUTH_TOO_MANY_ACCOUNTS_FROM_IP,
     *     AUTH_EMAIL_ALREADY_REGISTERED, AUTH_PHONE_ALREADY_USED, AUTH_OTP_RESEND_TOO_SOON
     */
    @Transactional // mở transaction ghi: lưu user và OTP cùng lúc, lỗi giữa chừng thì hoàn tác hết
    public OtpSentResponse register(RegisterRequest request, String registrationIp) {
        String email = normalizeEmail(request.email());
        checkEmailNotDisposable(email);
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
            checkIpUnderAccountLimit(registrationIp);
            user = new User(email, passwordHash, fullName, phone, false, UserStatus.PENDING_VERIFICATION);
            user.recordRegistrationIp(registrationIp);
            userRepository.save(user);
        }
        otpService.issue(user, OtpPurpose.REGISTER);
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
        otpService.verify(user, request.otp(), OtpPurpose.REGISTER);
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
            otpService.issue(pendingUser.get(), OtpPurpose.REGISTER);
        }
        return buildOtpSentResponse(email);
    }

    /**
     * Đổi mật khẩu cho người đang đăng nhập (FR-08, UC-04). Phải nhập đúng mật khẩu hiện tại; mật khẩu mới theo luật
     * của đăng ký (kiểm ở DTO), khác mật khẩu cũ và được lưu dạng BCrypt.
     *
     * @throws BusinessException USER_NOT_FOUND, AUTH_CURRENT_PASSWORD_INCORRECT, AUTH_NEW_PASSWORD_SAME_AS_CURRENT
     */
    @Transactional
    public void changePassword(long userId, ChangePasswordRequest request) {
        User user =
                userRepository.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.AUTH_CURRENT_PASSWORD_INCORRECT, null, "currentPassword", null);
        }
        // Hash BCrypt có muối ngẫu nhiên nên không so hai hash; so mật khẩu mới với hash cũ bằng matches().
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.AUTH_NEW_PASSWORD_SAME_AS_CURRENT, null, "newPassword", null);
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        // Listener chỉ gửi mail báo sau khi transaction này commit, nên đổi mật khẩu lỗi thì không có mail.
        eventPublisher.publishEvent(new PasswordChangedEvent(user.getEmail(), clock.instant()));
    }

    /**
     * Quên mật khẩu: gửi OTP đặt lại mật khẩu về email (FR-09, UC-04). Luôn trả cùng một nội dung; chỉ tài khoản ACTIVE
     * và chưa bị khoá mới được gửi mã. Email không có, chưa xác thực, bị khoá hoặc còn trong cooldown thì không gửi mail
     * và không báo lỗi, để không dò được email nào đã đăng ký.
     */
    @Transactional
    public OtpSentResponse forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> user = findUserAllowedToResetPassword(email);
        if (user.isPresent()) {
            issueResetOtpUnlessInCooldown(user.get());
        }
        return buildOtpSentResponse(email);
    }

    /**
     * Đặt lại mật khẩu bằng OTP đã gửi qua email (FR-09). Kiểm OTP trước, rồi mới kiểm mật khẩu mới khác mật khẩu cũ;
     * mật khẩu mới lưu dạng BCrypt và OTP bị xoá sau khi dùng. Email không có tài khoản hợp lệ báo như chưa có mã.
     *
     * @throws BusinessException AUTH_OTP_EXPIRED, AUTH_OTP_INVALID, AUTH_OTP_TOO_MANY_ATTEMPTS,
     *     AUTH_NEW_PASSWORD_SAME_AS_CURRENT
     */
    // noRollbackFor: giống verifyOtp, nhập sai mã vẫn phải lưu việc tăng otp_failed_count / xoá mã.
    @Transactional(noRollbackFor = BusinessException.class)
    public void resetPassword(ResetPasswordRequest request) {
        User user = findUserAllowedToResetPassword(normalizeEmail(request.email()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_OTP_EXPIRED));
        // Kiểm OTP trước: nếu kiểm trùng mật khẩu trước, người không có mã cũng dò được mật khẩu hiện tại.
        otpService.verify(user, request.otp(), OtpPurpose.RESET_PASSWORD);
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.AUTH_NEW_PASSWORD_SAME_AS_CURRENT, null, "newPassword", null);
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    // Chỉ tài khoản đăng nhập được mới cho đặt lại mật khẩu; PENDING dùng /resend-otp, BANNED hoặc bị khoá thì không.
    private Optional<User> findUserAllowedToResetPassword(String email) {
        return userRepository
                .findByEmail(email)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE && user.isActive());
    }

    // Còn trong cooldown: không gửi mã mới nhưng vẫn trả 200 như email không tồn tại, vì 429 sẽ lộ email có tài khoản.
    // issue() ném lỗi trước khi đổi dữ liệu và OtpService không có @Transactional, nên bắt lỗi ở đây không làm hỏng
    // transaction.
    private void issueResetOtpUnlessInCooldown(User user) {
        try {
            otpService.issue(user, OtpPurpose.RESET_PASSWORD);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != AuthErrorCode.AUTH_OTP_RESEND_TOO_SOON) {
                throw exception;
            }
        }
    }

    private Optional<User> findPendingUser(String email) {
        return userRepository.findByEmail(email).filter(user -> user.getStatus() == UserStatus.PENDING_VERIFICATION);
    }

    // field = "email" để FE hiện lỗi ngay dưới ô email.
    private void checkEmailNotDisposable(String email) {
        if (disposableEmailDomains.isDisposable(email)) {
            throw new BusinessException(AuthErrorCode.AUTH_EMAIL_DOMAIN_NOT_ALLOWED, null, "email", null);
        }
    }

    // Không lấy được IP (null, ví dụ khi gọi thẳng service) thì không có gì để đếm, bỏ qua kiểm tra.
    private void checkIpUnderAccountLimit(String registrationIp) {
        if (registrationIp == null || registrationIp.isBlank()) {
            return;
        }
        Instant since = clock.instant().minus(IP_LIMIT_WINDOW_HOURS, ChronoUnit.HOURS);
        long accountsFromIp = userRepository.countByRegistrationIpAndCreatedAtAfter(registrationIp, since);
        if (accountsFromIp >= configService.getInt(ConfigKey.ACCOUNTS_PER_IP_24H)) {
            throw new BusinessException(AuthErrorCode.AUTH_TOO_MANY_ACCOUNTS_FROM_IP);
        }
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
        // Cấm (BANNED) kiểm trước: tài khoản vừa bị khoá vừa bị cấm thì báo cấm.
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(AuthErrorCode.AUTH_ACCOUNT_BANNED);
        }
        // Khoá tài khoản là is_active = false (status vẫn giữ nguyên).
        if (!user.isActive()) {
            String reason = activityLogQueryService != null
                    ? activityLogQueryService
                            .findLatestReason(ActivityType.USER_LOCK.name(), user.getId())
                            .map(String::trim)
                            .filter(r -> !r.isEmpty())
                            .orElse(null)
                    : null;
            if (reason != null) {
                throw new BusinessException(
                        AuthErrorCode.AUTH_ACCOUNT_LOCKED,
                        "Tài khoản đã bị khoá. Lý do: " + reason,
                        null,
                        Map.of("reason", reason));
            }
            throw new BusinessException(AuthErrorCode.AUTH_ACCOUNT_LOCKED);
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.issue(user.getId(), user.isAdmin());
        long expiresInSeconds = jwtTtlMinutes * SECONDS_PER_MINUTE;
        return new AuthResponse(accessToken, TOKEN_TYPE, expiresInSeconds, userMapper.toAuthUserResponse(user));
    }
}
