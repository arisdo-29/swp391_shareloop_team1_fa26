package com.shareloop.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.entity.*;
import com.shareloop.report.repository.ReportRepository;
import com.shareloop.request.dto.RequestChatContext;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.service.RequestService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

class AdminReportQueryServiceTest {
    final ReportRepository reports = mock(ReportRepository.class);
    final UserService users = mock(UserService.class);
    final ItemQueryService items = mock(ItemQueryService.class);
    final RequestService requests = mock(RequestService.class);
    final ReportEvidenceService evidence = mock(ReportEvidenceService.class);
    final AdminReportQueryService service = new AdminReportQueryService(reports, users, items, requests, evidence);

    Report report(TargetType type, Long itemId, Long targetId, Long requestId) {
        var report =
                new Report(10L, type, itemId, targetId, requestId, ReportReason.OTHER, "Nội dung", ReportStage.BEFORE);
        ReflectionTestUtils.setField(report, "id", 1L);
        when(reports.findById(1L)).thenReturn(Optional.of(report));
        when(users.findBriefs(any()))
                .thenReturn(Map.of(
                        10L, new UserBrief(10L, "Người gửi", "private@example.test"),
                        20L, new UserBrief(20L, "Người bị báo cáo", "private2@example.test")));
        return report;
    }

    @Test
    void itemDetailResolvesOwnerAndExistingResponseAndConclusion() {
        var report = report(TargetType.ITEM, 5L, null, null);
        when(items.getSummary(5L)).thenReturn(new ItemSummary(5L, 20L, OfferType.GIVE, ItemStatus.APPROVED));
        ReflectionTestUtils.setField(report, "response", "Giải trình đã lưu");
        Instant respondedAt = Instant.parse("2026-10-19T12:00:00Z");
        ReflectionTestUtils.setField(report, "respondedAt", respondedAt);
        report.conclude(com.shareloop.report.dto.ReportDecision.DISMISS, 2L, "Không có cơ sở");
        var result = service.detail(1L);
        assertThat(result.reporter().fullName()).isEqualTo("Người gửi");
        assertThat(result.reportedUser().id()).isEqualTo(20L);
        assertThat(result.relatedItemId()).isEqualTo(5L);
        assertThat(result.response()).isEqualTo("Giải trình đã lưu");
        assertThat(result.respondedAt()).isEqualTo(respondedAt);
        assertThat(result.resolution()).isEqualTo("Không có cơ sở");
        assertThat(result.report().status()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(result.starsDeducted()).isZero();
    }

    @Test
    void requestDetailResolvesOtherPartyAndItem() {
        report(TargetType.REQUEST, null, null, 7L);
        when(requests.getChatContext(7L))
                .thenReturn(new RequestChatContext(7L, RequestStatus.DISPUTED, 10L, 20L, 5L, null));
        var result = service.detail(1L);
        assertThat(result.reportedUser().id()).isEqualTo(20L);
        assertThat(result.relatedItemId()).isEqualTo(5L);
        assertThat(result.response()).isNull();
    }

    @Test
    void missingRelatedItemDoesNotLoseReport() {
        report(TargetType.ITEM, 5L, null, null);
        when(items.getSummary(5L)).thenThrow(new BusinessException(com.shareloop.item.ItemErrorCode.ITEM_NOT_FOUND));
        var result = service.detail(1L);
        assertThat(result.report().id()).isEqualTo(1L);
        assertThat(result.reportedUser()).isNull();
    }

    @Test
    void list_addsReporterNameAndTargetNameForEachTargetType() {
        // given: một hồ sơ về bài đăng, một về người dùng, một về giao dịch
        var itemReport = reportWithId(1L, TargetType.ITEM, 5L, null, null);
        var userReport = reportWithId(2L, TargetType.USER, null, 20L, null);
        var requestReport = reportWithId(3L, TargetType.REQUEST, null, null, 7L);
        when(reports.findForAdmin(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(itemReport, userReport, requestReport)));
        when(requests.getChatContext(7L))
                .thenReturn(new RequestChatContext(7L, RequestStatus.DISPUTED, 10L, 20L, 6L, null));
        when(items.findTitles(any())).thenReturn(Map.of(5L, "Áo khoác gió", 6L, "Xe đạp mini"));
        when(users.findBriefs(any()))
                .thenReturn(Map.of(
                        10L, new UserBrief(10L, "Người gửi", "private@example.test"),
                        20L, new UserBrief(20L, "Người bị báo cáo", "private2@example.test")));

        // when
        var page = service.list(null, null, 0, 20);

        // then
        assertThat(page.items()).hasSize(3);
        assertThat(page.items().get(0).reporterName()).isEqualTo("Người gửi");
        assertThat(page.items().get(0).targetName()).isEqualTo("Áo khoác gió");
        assertThat(page.items().get(1).targetName()).isEqualTo("Người bị báo cáo");
        assertThat(page.items().get(2).targetName()).isEqualTo("Xe đạp mini");
    }

    @Test
    void list_whenNamesCannotBeResolved_leavesThemNullInsteadOfFailing() {
        // given: người gửi đã bị xoá mềm và giao dịch không còn
        var requestReport = reportWithId(3L, TargetType.REQUEST, null, null, 7L);
        when(reports.findForAdmin(any(), any(), any())).thenReturn(new PageImpl<>(List.of(requestReport)));
        when(requests.getChatContext(7L))
                .thenThrow(new BusinessException(com.shareloop.request.RequestErrorCode.REQUEST_NOT_FOUND));
        when(users.findBriefs(any())).thenReturn(Map.of());

        // when
        var page = service.list(null, null, 0, 20);

        // then
        assertThat(page.items().get(0).reporterName()).isNull();
        assertThat(page.items().get(0).targetName()).isNull();
    }

    @Test
    void detail_includesRelatedItemTitleAndResolverName() {
        // given
        var report = report(TargetType.ITEM, 5L, null, null);
        when(items.getSummary(5L)).thenReturn(new ItemSummary(5L, 20L, OfferType.GIVE, ItemStatus.APPROVED));
        when(items.findTitles(List.of(5L))).thenReturn(Map.of(5L, "Áo khoác gió"));
        report.conclude(com.shareloop.report.dto.ReportDecision.DISMISS, 2L, "Không có cơ sở");
        when(users.findBriefs(any()))
                .thenReturn(Map.of(
                        10L, new UserBrief(10L, "Người gửi", "private@example.test"),
                        20L, new UserBrief(20L, "Người bị báo cáo", "private2@example.test"),
                        2L, new UserBrief(2L, "Admin Hà", "admin@example.test")));

        // when
        var result = service.detail(1L);

        // then
        assertThat(result.relatedItemTitle()).isEqualTo("Áo khoác gió");
        assertThat(result.report().targetName()).isEqualTo("Áo khoác gió");
        assertThat(result.report().reporterName()).isEqualTo("Người gửi");
        assertThat(result.resolvedByName()).isEqualTo("Admin Hà");
    }

    private Report reportWithId(long id, TargetType type, Long itemId, Long targetId, Long requestId) {
        var report =
                new Report(10L, type, itemId, targetId, requestId, ReportReason.OTHER, "Nội dung", ReportStage.BEFORE);
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }

    @Test
    void unknownReportUsesBusinessError() {
        assertThatThrownBy(() -> service.detail(99L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_NOT_FOUND));
        verifyNoInteractions(users, items, requests);
    }
}
