package com.shareloop.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.user.UserErrorCode;
import com.shareloop.user.event.UserLockedEvent;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AdminUserServiceTest {

    private final UserService userService = mock(UserService.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userService, activityLogService, eventPublisher);
    }

    @Test
    @DisplayName("Khoá user hợp lệ: gọi userService.lock đúng 1 lần và ghi nhật ký hoạt động")
    void lock_validUser_callsUserServiceAndLogsActivity() {
        Long adminId = 1L;
        Long targetUserId = 10L;
        String reason = "Nhiều báo cáo lừa đảo đã được xác minh";

        adminUserService.lock(adminId, targetUserId, reason);

        verify(userService).lock(targetUserId, reason);
        verify(eventPublisher).publishEvent(any(UserLockedEvent.class));
        verify(activityLogService)
                .log(
                        "USER_LOCK",
                        adminId,
                        "USER",
                        targetUserId,
                        Map.<String, Object>of("cancelledRequests", 0, "releasedCredit", 0L),
                        reason);
    }

    @Test
    @DisplayName("Khoá user: số giao dịch đã huỷ và Credit đã nhả do listener cộng vào được ghi vào nhật ký")
    void lock_whenListenersCleanUp_logsCleanupTotals() {
        // given
        doAnswer(invocation -> {
                    UserLockedEvent event = invocation.getArgument(0);
                    event.cleanup().addCancelledRequests(2);
                    event.cleanup().addReleasedCredit(15);
                    return null;
                })
                .when(eventPublisher)
                .publishEvent(any(UserLockedEvent.class));

        // when
        adminUserService.lock(1L, 10L, "Vi phạm");

        // then
        verify(activityLogService)
                .log(
                        "USER_LOCK",
                        1L,
                        "USER",
                        10L,
                        Map.<String, Object>of("cancelledRequests", 2, "releasedCredit", 15L),
                        "Vi phạm");
    }

    @Test
    @DisplayName("Admin tự khoá chính mình ném lỗi USER_CANNOT_LOCK_SELF và không ghi log")
    void lock_self_throwsCannotLockSelf() {
        Long adminId = 1L;
        String reason = "Tự khoá";

        assertThatThrownBy(() -> adminUserService.lock(adminId, adminId, reason))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_CANNOT_LOCK_SELF));

        verify(userService, never()).lock(anyLong(), any());
        verify(activityLogService, never()).log(any(), any(), any(), any(), any(String.class), any());
    }

    @Test
    @DisplayName("Khoá user không tồn tại ném lỗi USER_NOT_FOUND và không ghi log")
    void lock_userNotFound_throwsExceptionAndNoLog() {
        Long adminId = 1L;
        Long targetUserId = 999L;
        String reason = "User không tồn tại";

        doThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND))
                .when(userService)
                .lock(targetUserId, reason);

        assertThatThrownBy(() -> adminUserService.lock(adminId, targetUserId, reason))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex ->
                        assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));

        verify(activityLogService, never()).log(any(), any(), any(), any(), any(String.class), any());
    }

    @Test
    @DisplayName("Mở khoá user hợp lệ: gọi userService.unlock đúng 1 lần và ghi nhật ký hoạt động")
    void unlock_validUser_callsUserServiceAndLogsActivity() {
        Long adminId = 1L;
        Long targetUserId = 10L;
        String reason = "Đã giải trình hợp lệ";

        adminUserService.unlock(adminId, targetUserId, reason);

        verify(userService).unlock(targetUserId);
        verify(activityLogService).log("USER_UNLOCK", adminId, "USER", targetUserId, (String) null, reason);
    }

    @Test
    @DisplayName("Mở khoá user không tồn tại ném lỗi USER_NOT_FOUND và không ghi log")
    void unlock_userNotFound_throwsExceptionAndNoLog() {
        Long adminId = 1L;
        Long targetUserId = 999L;
        String reason = "User không tồn tại";

        doThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND))
                .when(userService)
                .unlock(targetUserId);

        assertThatThrownBy(() -> adminUserService.unlock(adminId, targetUserId, reason))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex ->
                        assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));

        verify(activityLogService, never()).log(any(), any(), any(), any(), any(String.class), any());
    }
}
