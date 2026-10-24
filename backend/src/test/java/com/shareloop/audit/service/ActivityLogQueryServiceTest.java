package com.shareloop.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.AuditErrorCode;
import com.shareloop.audit.dto.ActivityLogDto;
import com.shareloop.audit.dto.ActivityTypeDto;
import com.shareloop.audit.entity.ActivityLog;
import com.shareloop.audit.repository.ActivityLogRepository;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("unchecked")
class ActivityLogQueryServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-15T12:00:00Z");

    private final ActivityLogRepository repository = mock(ActivityLogRepository.class);
    private final UserService userService = mock(UserService.class);
    private final ItemQueryService itemQueryService = mock(ItemQueryService.class);
    private final ActivityLogQueryService service =
            new ActivityLogQueryService(repository, userService, itemQueryService);

    @BeforeEach
    void setUp() {
        when(userService.findBriefs(anyCollection())).thenReturn(Map.of());
        when(itemQueryService.findTitles(anyCollection())).thenReturn(Map.of());
    }

    @Test
    void search_whenFromAfterTo_throwsInvalidDateRange() {
        // given
        LocalDate from = LocalDate.of(2026, 10, 20);
        LocalDate to = LocalDate.of(2026, 10, 10);

        // when / then
        assertThatThrownBy(() -> service.search(1L, "REVIEW_APPROVE", from, to, 0, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(AuditErrorCode.INVALID_DATE_RANGE));
    }

    @Test
    void search_withValidParameters_sortsNewestFirst() {
        // given
        givenLogs(List.of());

        // when
        service.search(1L, "REVIEW_APPROVE", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 17), 0, 20);

        // then
        Pageable pageable = capturePageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void search_whenPageNegativeAndSizeTooLarge_clampsPageAndSize() {
        // given
        givenLogs(List.of());

        // when
        service.search(null, null, null, null, -5, 500);

        // then
        Pageable pageable = capturePageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
    }

    @Test
    void search_whenActorAndItemFound_returnsActorNameAndItemTitle() {
        // given
        givenLogs(List.of(newLog(1L, ActivityType.REVIEW_REJECT, 3L, "ITEM", 42L, null, "Ảnh mờ")));
        when(userService.findBriefs(anyCollection()))
                .thenReturn(Map.of(3L, new UserBrief(3L, "Nguyễn Văn A", "a@fpt.edu.vn")));
        when(itemQueryService.findTitles(anyCollection())).thenReturn(Map.of(42L, "Áo khoác size M"));

        // when
        ActivityLogDto dto = firstRow(service.search(null, null, null, null, 0, 20));

        // then
        assertThat(dto.actorName()).isEqualTo("Nguyễn Văn A");
        assertThat(dto.actorEmail()).isEqualTo("a@fpt.edu.vn");
        assertThat(dto.typeLabel()).isEqualTo("Từ chối bài");
        assertThat(dto.targetTitle()).isEqualTo("Áo khoác size M");
        assertThat(dto.detail()).isEqualTo("Ảnh mờ");
    }

    @Test
    void search_whenActorIdNull_returnsSystemActor() {
        // given
        givenLogs(List.of(newLog(1L, ActivityType.STARS, null, "USER", 7L, Map.of("amount", 1), "Vi phạm")));

        // when
        ActivityLogDto dto = firstRow(service.search(null, null, null, null, 0, 20));

        // then
        assertThat(dto.actorName()).isEqualTo("Hệ thống");
        assertThat(dto.actorEmail()).isNull();
    }

    @Test
    void search_whenUserNotFound_returnsPlaceholderName() {
        // given
        givenLogs(List.of(newLog(1L, ActivityType.USER_LOCK, 9L, "USER", 7L, null, "Spam")));

        // when
        ActivityLogDto dto = firstRow(service.search(null, null, null, null, 0, 20));

        // then
        assertThat(dto.actorName()).isEqualTo("Người dùng #9");
        assertThat(dto.targetTitle()).isEqualTo("Người dùng #7");
    }

    @Test
    void search_withManyRows_callsEachServiceOncePerPage() {
        // given
        givenLogs(List.of(
                newLog(1L, ActivityType.REVIEW_APPROVE, 3L, "ITEM", 42L, null, null),
                newLog(2L, ActivityType.REVIEW_REJECT, 4L, "ITEM", 43L, null, "Sai danh mục"),
                newLog(3L, ActivityType.USER_LOCK, 3L, "USER", 5L, null, "Spam")));

        // when
        service.search(null, null, null, null, 0, 20);

        // then
        ArgumentCaptor<Collection<Long>> userIds = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<Long>> itemIds = ArgumentCaptor.forClass(Collection.class);
        verify(userService, times(1)).findBriefs(userIds.capture());
        verify(itemQueryService, times(1)).findTitles(itemIds.capture());
        assertThat(userIds.getValue()).containsExactlyInAnyOrder(3L, 4L, 5L);
        assertThat(itemIds.getValue()).containsExactlyInAnyOrder(42L, 43L);
    }

    @Test
    void search_whenConfigUpdate_returnsReadableDetailAndConfigKey() {
        // given
        Map<String, Object> data = Map.of("key", "POST_FEE", "oldValue", "5", "newValue", "10");
        givenLogs(List.of(newLog(1L, ActivityType.CONFIG_UPDATE, 3L, "CONFIG", null, data, "Tăng phí")));

        // when
        ActivityLogDto dto = firstRow(service.search(null, null, null, null, 0, 20));

        // then
        assertThat(dto.targetTitle()).isEqualTo("POST_FEE");
        assertThat(dto.detail()).isEqualTo("POST_FEE: 5 → 10");
    }

    @Test
    void search_whenReviewApprove_detailIsApproveLabel() {
        // given
        Map<String, Object> snapshot = Map.of("checks", List.of());
        givenLogs(List.of(newLog(1L, ActivityType.REVIEW_APPROVE, 3L, "ITEM", 42L, snapshot, null)));

        // when
        ActivityLogDto dto = firstRow(service.search(null, null, null, null, 0, 20));

        // then
        assertThat(dto.detail()).isEqualTo("Duyệt bài");
    }

    @Test
    void search_whenLegacyAiUsage_returnsSearchOutcomeInDetail() {
        Map<String, Object> data = Map.of("role", "SEARCH", "outcome", "FAILED", "durationMs", 123);
        givenLogs(List.of(new ActivityLog("AiUsage", 3L, "AI_SEARCH", null, data, null, CREATED_AT)));

        ActivityLogDto dto = firstRow(service.search(null, null, null, null, 0, 20));

        assertThat(dto.typeLabel()).isEqualTo("Gọi AI kiểm duyệt");
        assertThat(dto.detail()).isEqualTo("Tìm đồ AI: FAILED (123 ms)");
    }

    @Test
    void search_whenAiUsage_returnsModerationOutcomeInDetail() {
        Map<String, Object> data = Map.of("outcome", "SUCCESS", "durationMs", 300);
        givenLogs(List.of(newLog(1L, ActivityType.AI_USAGE, 3L, "ITEM", 42L, data, null)));

        ActivityLogDto dto = firstRow(service.search(null, null, null, null, 0, 20));

        assertThat(dto.detail()).isEqualTo("Kiểm duyệt AI: SUCCESS (300 ms)");
    }

    @Test
    void search_whenReportAndRequestTargets_returnsNumberedTitles() {
        // given
        givenLogs(List.of(
                newLog(1L, ActivityType.REPORT_UPHELD, 3L, "REPORT", 11L, null, null),
                newLog(2L, ActivityType.POINTS, 4L, "REQUEST", 12L, null, null)));

        // when
        List<ActivityLogDto> rows =
                service.search(null, null, null, null, 0, 20).getContent();

        // then
        assertThat(rows.get(0).targetTitle()).isEqualTo("Báo cáo #11");
        assertThat(rows.get(1).targetTitle()).isEqualTo("Yêu cầu #12");
    }

    @Test
    void listTypes_returnsEveryActivityTypeWithLabel() {
        // when
        List<ActivityTypeDto> types = service.listTypes();

        // then
        assertThat(types).hasSize(ActivityType.values().length);
        assertThat(types).contains(new ActivityTypeDto("AI_USAGE", "Gọi AI kiểm duyệt"));
    }

    private void givenLogs(List<ActivityLog> logs) {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(logs));
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageableCaptor.capture());
        return pageableCaptor.getValue();
    }

    private static ActivityLogDto firstRow(Page<ActivityLogDto> page) {
        assertThat(page.getContent()).hasSize(1);
        return page.getContent().get(0);
    }

    private static ActivityLog newLog(
            Long id,
            ActivityType type,
            Long actorId,
            String targetType,
            Long targetId,
            Map<String, Object> data,
            String reason) {
        ActivityLog log = new ActivityLog(type.name(), actorId, targetType, targetId, data, reason, CREATED_AT);
        ReflectionTestUtils.setField(log, "id", id);
        return log;
    }
}
