package com.shareloop.user.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.user.UserErrorCode;
import com.shareloop.user.dto.AdminAccountRole;
import com.shareloop.user.dto.AdminAccountState;
import com.shareloop.user.dto.AdminUserSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý khoá và mở khoá tài khoản người dùng cho Admin (FR-107, Issue #116).
 * Đảm bảo thực thi cập nhật trạng thái người dùng và ghi nhật ký hoạt động trong cùng một transaction.
 */
@Service
public class AdminUserService {

    private final UserService userService;
    private final ActivityLogService activityLogService;

    public AdminUserService(UserService userService, ActivityLogService activityLogService) {
        this.userService = userService;
        this.activityLogService = activityLogService;
    }

    /**
     * Danh sách tài khoản cho Admin, có tìm kiếm và lọc theo trạng thái, vai trò (FR-107). Chỉ đọc nên không ghi
     * nhật ký.
     */
    public PageResponse<AdminUserSummary> list(
            String keyword, AdminAccountState state, AdminAccountRole role, int page, int size) {
        return userService.searchAccounts(keyword, state, role, page, size);
    }

    /**
     * Khoá tài khoản người dùng và ghi nhận vào nhật ký hành động (FR-107).
     *
     * @param adminId ID quản trị viên thực hiện
     * @param userId ID người dùng cần khoá
     * @param reason lý do khoá
     */
    @Transactional
    public void lock(Long adminId, Long userId, String reason) {
        if (adminId != null && adminId.equals(userId)) {
            throw new BusinessException(UserErrorCode.USER_CANNOT_LOCK_SELF);
        }

        userService.lock(userId, reason);
        activityLogService.log(ActivityType.USER_LOCK.name(), adminId, "USER", userId, (String) null, reason);
    }

    /**
     * Mở khoá tài khoản người dùng và ghi nhận vào nhật ký hành động (FR-107).
     *
     * @param adminId ID quản trị viên thực hiện
     * @param userId ID người dùng cần mở khoá
     * @param reason lý do mở khoá
     */
    @Transactional
    public void unlock(Long adminId, Long userId, String reason) {
        userService.unlock(userId);
        activityLogService.log(ActivityType.USER_UNLOCK.name(), adminId, "USER", userId, (String) null, reason);
    }
}
