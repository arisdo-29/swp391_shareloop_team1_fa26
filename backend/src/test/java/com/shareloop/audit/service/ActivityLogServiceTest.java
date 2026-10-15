package com.shareloop.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.entity.ActivityLog;
import com.shareloop.audit.repository.ActivityLogRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ActivityLogServiceTest {

    private final ActivityLogRepository repository = mock(ActivityLogRepository.class);
    private final ActivityLogService service = new ActivityLogService(repository);

    @Test
    void logWithValidParametersSavesEntity() {
        when(repository.save(any(ActivityLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.log("USER_LOCK", 1L, "USER", 2L, "{\"action\":\"lock\"}", "Vi phạm quy định");

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void logWithNullParametersDoesNotThrow() {
        when(repository.save(any(ActivityLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.log(null, null, null, null, (String) null, null);

        verify(repository).save(any(ActivityLog.class));
    }

    @Test
    void logWithMapDataSavesCorrectly() {
        when(repository.save(any(ActivityLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.log(1L, "CONFIG_UPDATED", "CONFIG", null, Map.of("key", "val"), "reason");

        verify(repository).save(any(ActivityLog.class));
    }
}

