package com.shareloop.user.service;

import com.shareloop.catalog.service.AreaService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.service.RequestQueryService;
import com.shareloop.user.UserErrorCode;
import com.shareloop.user.dto.PublicProfileResponse;
import com.shareloop.user.entity.User;
import com.shareloop.user.mapper.UserMapper;
import com.shareloop.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hồ sơ công khai của người dùng (trang "Hồ sơ" khi xem người khác). Tách khỏi UserService để không phải đổi constructor
 * mà nhiều test của UserService đang dùng.
 *
 * <p>Hạng, sao, tỉ lệ đúng mô tả lấy từ ReputationService; số giao dịch hoàn tất lấy từ RequestQueryService. Service này
 * không tự tính lại các con số đó.
 */
@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AreaService areaService;
    private final ReputationService reputationService;
    private final RequestQueryService requestQueryService;

    public UserProfileService(
            UserRepository userRepository,
            UserMapper userMapper,
            AreaService areaService,
            ReputationService reputationService,
            RequestQueryService requestQueryService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.areaService = areaService;
        this.reputationService = reputationService;
        this.requestQueryService = requestQueryService;
    }

    /**
     * Hồ sơ công khai của một người dùng: họ tên, ảnh, khu vực, hạng, sao, tỉ lệ đúng mô tả, số giao dịch hoàn tất.
     * Không trả email, số điện thoại, Credit, điểm thưởng.
     *
     * @throws BusinessException USER_NOT_FOUND khi không có tài khoản, hoặc tài khoản chưa xác thực, bị khoá, bị cấm
     */
    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(long userId) {
        User user =
                userRepository.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        // Tài khoản bị khoá, cấm hoặc chưa xác thực: trả 404 như không tồn tại, không lộ lý do.
        if (!user.isPubliclyVisible()) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        String district = findDistrictName(user.getAreaId());
        ReputationSummary reputation = reputationService.getReputationSummary(userId);
        long completedTransactions = requestQueryService.countCompletedByUser(userId);
        return userMapper.toPublicProfile(user, district, reputation, completedTransactions);
    }

    // Người dùng chưa chọn khu vực thì không có tên khu vực, frontend ẩn dòng này.
    private String findDistrictName(Long areaId) {
        if (areaId == null) {
            return null;
        }
        return areaService.findName(areaId).orElse(null);
    }
}
