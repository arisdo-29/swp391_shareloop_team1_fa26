package com.shareloop.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.entity.ActivityLog;
import com.shareloop.audit.repository.ActivityLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ActivityLogServiceTest {

    private final ActivityLogRepository repository = mock(ActivityLogRepository.class);
    private final Instant fixedInstant = Instant.parse("2026-10-15T12:00:00Z");
    private final Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
    private final ActivityLogService service = new ActivityLogService(repository, clock);

    @Test
    void logWithValidParametersSavesEntityWithCreatedAtFromClock() {
        when(repository.save(any(ActivityLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.log("USER_LOCK", 1L, "USER", 2L, "{\"action\":\"lock\"}", "Vi phạm quy định");

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(repository).save(captor.capture());
        ActivityLog saved = captor.getValue();
        assertThat(saved).isNotNull();
        assertThat(saved.getType()).isEqualTo("USER_LOCK");
        assertThat(saved.getActorId()).isEqualTo(1L);
        assertThat(saved.getTargetType()).isEqualTo("USER");
        assertThat(saved.getTargetId()).isEqualTo(2L);
        assertThat(saved.getData()).containsEntry("raw", "{\"action\":\"lock\"}");
        assertThat(saved.getReason()).isEqualTo("Vi phạm quy định");
        assertThat(saved.getCreatedAt()).isEqualTo(fixedInstant);
    }

    @Test
    void logWithNullOrBlankTypeThrowsException() {
        assertThatThrownBy(() -> service.log(null, 1L, "USER", 2L, (String) null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Activity log type must not be null or blank");

        assertThatThrownBy(() -> service.log("  ", 1L, "USER", 2L, (String) null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Activity log type must not be null or blank");
    }

    @Test
    void logWithNullDataAndReasonSavesSuccessfully() {
        when(repository.save(any(ActivityLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.log("REVIEW_REJECT", 1L, "ITEM", 10L, (String) null, null);

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(repository).save(captor.capture());
        ActivityLog saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo("REVIEW_REJECT");
        assertThat(saved.getActorId()).isEqualTo(1L);
        assertThat(saved.getData()).isNull();
        assertThat(saved.getReason()).isNull();
        assertThat(saved.getCreatedAt()).isEqualTo(fixedInstant);
    }

    @Test
    void logWithMapDataSavesCorrectly() {
        when(repository.save(any(ActivityLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.log("CONFIG_UPDATE", 1L, "CONFIG", null, Map.of("key", "val"), "reason");

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(repository).save(captor.capture());
        ActivityLog saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo("CONFIG_UPDATE");
        assertThat(saved.getData()).containsEntry("key", "val");
        assertThat(saved.getCreatedAt()).isEqualTo(fixedInstant);
    }
}
