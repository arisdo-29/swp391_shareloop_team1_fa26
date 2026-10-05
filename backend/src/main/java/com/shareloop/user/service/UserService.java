package com.shareloop.user.service;

import com.shareloop.auth.dto.AuthUserResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.user.UserErrorCode;
import com.shareloop.user.dto.ContactInfo;
import com.shareloop.user.mapper.UserMapper;
import com.shareloop.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service cửa ngõ của module user (Lộ trình 4.7, 4.8). Module khác muốn khoá tài khoản hoặc lấy thông tin liên lạc
 * phải gọi qua đây, không inject repository của user.
 *
 * <p>Khoá tài khoản là {@code is_active = false}; cột {@code status} không đổi (ERD 7.11, SRS FR-107).
 *
 * <p>Người gọi: admin (AdminUserService: khoá, mở khoá), reputation (khoá khi vi phạm nặng), request (lấy thông tin
 * liên lạc khi hai bên chốt lịch).
 */
@Service
public class UserService {

    private static final String TODO = "TODO BE1 - tuan 4";

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    /**
     * Thông tin người dùng đang đăng nhập (GET /api/v1/me).
     *
     * <p>Transaction: read-only.
     *
     * @throws com.shareloop.common.exception.BusinessException USER_NOT_FOUND khi tài khoản không còn tồn tại
     */
    @Transactional(readOnly = true) // chỉ đọc DB: mở transaction read-only
    public AuthUserResponse getMe(long userId) {
        return userRepository
                .findById(userId)
                .map(userMapper::toAuthUserResponse)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    /**
     * Khoá tài khoản (FR-107, UC-50).
     *
     * <p>Quy tắc: đặt {@code is_active = false}, không đổi {@code status}; tài khoản bị khoá không đăng nhập và
     * không giao dịch được. SRS FR-107 không bắt buộc lý do; người gọi ghi nhật ký qua ActivityLogService.
     *
     * <p>Transaction: REQUIRED, tham gia transaction của người gọi. Người gọi: admin, reputation.
     *
     * @param userId tài khoản cần khoá
     * @param reason lý do khoá, có thể null
     * @throws com.shareloop.common.exception.BusinessException USER_NOT_FOUND khi không có user;
     *     USER_ALREADY_LOCKED khi đã khoá
     */
    public void lock(long userId, String reason) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Mở khoá tài khoản (FR-107, UC-50).
     *
     * <p>Quy tắc: đặt {@code is_active = true}, không đổi {@code status}.
     *
     * <p>Transaction: REQUIRED, tham gia transaction của người gọi. Người gọi: admin, reputation.
     *
     * @param userId tài khoản cần mở khoá
     * @throws com.shareloop.common.exception.BusinessException USER_NOT_FOUND khi không có user;
     *     USER_NOT_LOCKED khi tài khoản đang hoạt động
     */
    public void unlock(long userId) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Lấy thông tin liên lạc để trao cho đối tác khi hai bên xác nhận lịch (UC-26, UC-62).
     *
     * <p>Quy tắc: BR-T11 hai bên xác nhận lịch thì trao email và số điện thoại cho nhau; BR-T12 đã trao thì không
     * ẩn lại. Chỉ trả họ tên, email, số điện thoại; email luôn có, không trả dữ liệu nhạy cảm khác. Người gọi
     * (request) chỉ gọi sau khi Request đã sang LogisticsConfirmed.
     *
     * <p>Transaction: read-only.
     *
     * @param userId người cần lấy thông tin
     * @return userId, fullName, email, phone
     * @throws com.shareloop.common.exception.BusinessException USER_NOT_FOUND khi không có user
     */
    public ContactInfo findContact(long userId) {
        throw new UnsupportedOperationException(TODO);
    }
}
