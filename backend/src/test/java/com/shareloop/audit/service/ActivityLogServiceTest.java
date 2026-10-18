package com.shareloop.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.AuditErrorCode;
import com.shareloop.audit.dto.ActivityLogDto;
import com.shareloop.audit.entity.ActivityLog;
import com.shareloop.audit.repository.ActivityLogRepository;
import com.shareloop.common.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    @DisplayName("search: from sau to ném BusinessException INVALID_DATE_RANGE")
    void search_fromAfterTo_throwsInvalidDateRange() {
        LocalDate from = LocalDate.of(2026, 10, 20);
        LocalDate to = LocalDate.of(2026, 10, 10);

        assertThatThrownBy(() -> service.search(1L, "APPROVE_ITEM", from, to, 0, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(AuditErrorCode.INVALID_DATE_RANGE));
    }

    @Test
    @DisplayName("search: phân trang mặc định sắp xếp mới nhất trước và map DTO chính xác")
    @SuppressWarnings("unchecked")
    void search_validParameters_queriesRepositoryAndMapsDto() {
        ActivityLog logEntity =
                new ActivityLog("APPROVE_ITEM", 1L, "ITEM", 42L, Map.of("status", "APPROVED"), "Hợp lệ", fixedInstant);
        ReflectionTestUtils.setField(logEntity, "id", 100L);

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(logEntity)));

        Page<ActivityLogDto> result =
                service.search(1L, "APPROVE_ITEM", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 17), 0, 20);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        ActivityLogDto dto = result.getContent().get(0);
        assertThat(dto.id()).isEqualTo(100L);
        assertThat(dto.actorId()).isEqualTo(1L);
        assertThat(dto.type()).isEqualTo("APPROVE_ITEM");
        assertThat(dto.targetType()).isEqualTo("ITEM");
        assertThat(dto.targetId()).isEqualTo(42L);
        assertThat(dto.reason()).isEqualTo("Hợp lệ");
        assertThat(dto.detail()).isEqualTo("Hợp lệ");
        assertThat(dto.createdAt()).isEqualTo(fixedInstant);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("search: giới hạn size tối đa 100 và page không âm")
    @SuppressWarnings("unchecked")
    void search_clampsPageAndSize() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.search(null, null, null, null, -5, 500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(100);
    }
}
