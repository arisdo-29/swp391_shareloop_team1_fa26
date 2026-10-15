package com.shareloop.user.service;

import com.shareloop.catalog.service.AreaService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.media.service.MediaService;
import com.shareloop.user.UserErrorCode;
import com.shareloop.user.dto.ContactInfo;
import com.shareloop.user.dto.MeResponse;
import com.shareloop.user.dto.UpdateProfileRequest;
import com.shareloop.user.entity.User;
import com.shareloop.user.mapper.UserMapper;
import com.shareloop.user.repository.UserRepository;
import java.util.List;
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
    private final AreaService areaService;
    private final MediaService mediaService;

    public UserService(
            UserRepository userRepository, UserMapper userMapper, AreaService areaService, MediaService mediaService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.areaService = areaService;
        this.mediaService = mediaService;
    }

    /**
     * Hồ sơ người dùng đang đăng nhập (GET /api/v1/me, FR-06).
     *
     * <p>Transaction: read-only.
     *
     * @throws com.shareloop.common.exception.BusinessException USER_NOT_FOUND khi tài khoản không còn tồn tại
     */
    @Transactional(readOnly = true) // chỉ đọc DB: mở transaction read-only
    public MeResponse getMe(long userId) {
        return userMapper.toMeResponse(findUser(userId));
    }

    /**
     * Cập nhật họ tên, khu vực mặc định, ảnh đại diện của chính mình (PUT /api/v1/me, FR-06, BR-U03). Không đổi
     * email và số điện thoại. Khu vực phải đang hoạt động; ảnh mới phải do chính người này upload và chưa gắn ở đâu.
     *
     * @throws com.shareloop.common.exception.BusinessException USER_NOT_FOUND, USER_AREA_INVALID; MEDIA_NOT_FOUND,
     *     MEDIA_NOT_OWNER, MEDIA_NOT_UPLOADED (từ MediaService)
     */
    @Transactional // gắn ảnh và lưu hồ sơ trong cùng transaction: một bước lỗi thì hoàn tác cả hai
    public MeResponse updateProfile(long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        checkAreaIsActive(request.areaId());
        attachAvatarIfChanged(user, request.avatarMediaId());
        user.updateProfile(request.fullName().trim(), request.areaId(), request.avatarMediaId());
        // Không cần gọi save(): user đang được JPA theo dõi, thay đổi tự ghi xuống DB khi transaction commit.
        return userMapper.toMeResponse(user);
    }

    private User findUser(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    // areaId null nghĩa là người dùng bỏ khu vực mặc định, không cần kiểm.
    private void checkAreaIsActive(Long areaId) {
        if (areaId == null) {
            return;
        }
        if (!areaService.isActiveArea(areaId)) {
            throw new BusinessException(UserErrorCode.USER_AREA_INVALID, null, "areaId", null);
        }
    }

    // Ảnh giữ nguyên thì đã ATTACHED từ lần trước; gọi attach lần nữa sẽ bị MediaService từ chối.
    private void attachAvatarIfChanged(User user, Long newAvatarMediaId) {
        if (newAvatarMediaId == null || newAvatarMediaId.equals(user.getAvatarMediaId())) {
            return;
        }
        // attach kiểm ảnh tồn tại, đúng người upload, còn UPLOADED rồi chuyển sang ATTACHED.
        mediaService.attach(List.of(newAvatarMediaId), user.getId());
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
