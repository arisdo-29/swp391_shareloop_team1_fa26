package com.shareloop.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
import com.shareloop.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit test nhánh lỗi của job dọn tài khoản chưa xác thực: một dòng lỗi không làm hỏng cả lượt. Các nhánh chọn dòng
 * và chạy lại kiểm trên DB thật ở PendingAccountCleanupServiceIT.
 */
@ExtendWith(MockitoExtension.class)
class PendingAccountCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");

    @Mock
    UserRepository userRepository;

    @Mock
    ConfigService configService;

    @Test
    void deleteExpiredPendingAccounts_whenOneAccountFails_stillDeletesTheOthers() {
        // given: hai tài khoản quá hạn; đọc tài khoản 1 bị lỗi DB
        // TransactionTemplate thật với transaction manager giả: vẫn chạy lambda, rollback khi lambda ném lỗi.
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        PendingAccountCleanupService cleanupService =
                new PendingAccountCleanupService(userRepository, configService, transactionTemplate);
        when(configService.getInt(ConfigKey.PENDING_ACCOUNT_TTL_HOURS)).thenReturn(24);
        when(userRepository.findCleanupCandidateIds(
                        eq(UserStatus.PENDING_VERIFICATION), any(Instant.class), eq(NOW), eq(0L), any(Limit.class)))
                .thenReturn(List.of(1L, 2L));
        when(userRepository.findCleanupCandidateIds(
                        eq(UserStatus.PENDING_VERIFICATION), any(Instant.class), eq(NOW), eq(2L), any(Limit.class)))
                .thenReturn(List.of());
        when(userRepository.findById(1L)).thenThrow(new IllegalStateException("database unavailable"));
        User expiredPendingUser = mock(User.class);
        when(expiredPendingUser.getStatus()).thenReturn(UserStatus.PENDING_VERIFICATION);
        when(expiredPendingUser.getCreatedAt()).thenReturn(NOW.minus(25, ChronoUnit.HOURS));
        when(userRepository.findById(2L)).thenReturn(Optional.of(expiredPendingUser));

        // when
        int deletedCount = cleanupService.deleteExpiredPendingAccounts(NOW);

        // then: tài khoản 1 bỏ qua (chỉ log), tài khoản 2 vẫn bị xoá mềm
        assertThat(deletedCount).isEqualTo(1);
        verify(expiredPendingUser).markDeleted();
    }
}
