package com.shareloop.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.AdminReportDetail.Evidence;
import com.shareloop.report.dto.MyReportSummary;
import com.shareloop.report.dto.ReportDecision;
import com.shareloop.report.dto.RespondentReportDetail;
import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStage;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.repository.ReportRepository;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.RequestChatContext;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.service.RequestService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MyReportQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-07T03:00:00Z");
    private static final long ME = 10L;

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final UserService userService = mock(UserService.class);
    private final ItemQueryService itemQueryService = mock(ItemQueryService.class);
    private final RequestService requestService = mock(RequestService.class);
    private final ReportEvidenceService reportEvidenceService = mock(ReportEvidenceService.class);
    private final MyReportQueryService service = new MyReportQueryService(
            reportRepository,
            userService,
            itemQueryService,
            requestService,
            reportEvidenceService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(itemQueryService.findTitles(any())).thenReturn(new HashMap<>(Map.of(5L, "Giáo trình Toán rời rạc")));
        when(userService.findBriefs(any()))
                .thenReturn(new HashMap<>(Map.of(20L, new UserBrief(20L, "Trần Thị Bình", "binh@example.test"))));
    }

    private Report report(long id, TargetType type, Long itemId, Long targetUserId, Long requestId) {
        Report report = new Report(
                ME, type, itemId, targetUserId, requestId, ReportReason.OTHER, "Mô tả riêng", ReportStage.BEFORE);
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }

    @Test
    void listSent_userReportConcluded_returnsTargetNameAndAdminResolution() {
        // given
        Report report = report(1L, TargetType.USER, null, 20L, null);
        report.conclude(ReportDecision.UPHOLD, 2L, "Đã xác minh vi phạm");
        when(reportRepository.findByReporterIdOrderByCreatedAtDescIdDesc(ME)).thenReturn(List.of(report));

        // when
        List<MyReportSummary> result = service.listSent(ME);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).targetName()).isEqualTo("Trần Thị Bình");
        assertThat(result.get(0).status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(result.get(0).resolution()).isEqualTo("Đã xác minh vi phạm");
    }

    @Test
    void listSent_requestReport_usesItemTitleOfTransaction() {
        // given
        Report report = report(2L, TargetType.REQUEST, null, 20L, 7L);
        when(reportRepository.findByReporterIdOrderByCreatedAtDescIdDesc(ME)).thenReturn(List.of(report));
        when(requestService.getChatContext(7L))
                .thenReturn(new RequestChatContext(7L, RequestStatus.DISPUTED, ME, 20L, 5L, null));

        // when
        List<MyReportSummary> result = service.listSent(ME);

        // then
        assertThat(result.get(0).targetName()).isEqualTo("Giáo trình Toán rời rạc");
    }

    @Test
    void listSent_transactionDeleted_keepsReportWithoutName() {
        // given
        Report report = report(3L, TargetType.REQUEST, null, 20L, 8L);
        when(reportRepository.findByReporterIdOrderByCreatedAtDescIdDesc(ME)).thenReturn(List.of(report));
        when(requestService.getChatContext(8L)).thenThrow(new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));

        // when
        List<MyReportSummary> result = service.listSent(ME);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).targetName()).isNull();
    }

    @Test
    void listAgainstMe_awaitingBeforeDeadline_responseWindowOpen() {
        // given
        Report report = report(4L, TargetType.ITEM, 5L, ME, null);
        report.awaitResponse(NOW.plusSeconds(3600));
        when(reportRepository.findByTargetUserIdAndResponseDueAtIsNotNullOrderByCreatedAtDescIdDesc(ME))
                .thenReturn(List.of(report));

        // when
        List<MyReportSummary> result = service.listAgainstMe(ME);

        // then
        assertThat(result.get(0).responseWindowOpen()).isTrue();
        assertThat(result.get(0).targetName()).isEqualTo("Giáo trình Toán rời rạc");
    }

    @Test
    void listAgainstMe_deadlinePassed_responseWindowClosed() {
        // given
        Report report = report(5L, TargetType.ITEM, 5L, ME, null);
        report.awaitResponse(NOW.minusSeconds(60));
        when(reportRepository.findByTargetUserIdAndResponseDueAtIsNotNullOrderByCreatedAtDescIdDesc(ME))
                .thenReturn(List.of(report));

        // when
        List<MyReportSummary> result = service.listAgainstMe(ME);

        // then
        assertThat(result.get(0).responseWindowOpen()).isFalse();
    }

    @Test
    void detailForRespondent_respondentWithDeadline_returnsDescriptionAndEvidence() {
        // given
        Report report = report(6L, TargetType.ITEM, 5L, ME, null);
        report.setEvidence("[12]");
        report.awaitResponse(NOW.plusSeconds(3600));
        when(reportRepository.findById(6L)).thenReturn(Optional.of(report));
        List<Evidence> evidence = List.of(new Evidence(12L, "/api/v1/reports/6/evidence/12", true));
        when(reportEvidenceService.describeForRespondent(6L, "[12]")).thenReturn(evidence);

        // when
        RespondentReportDetail result = service.detailForRespondent(ME, 6L);

        // then
        assertThat(result.description()).isEqualTo("Mô tả riêng");
        assertThat(result.evidence()).isEqualTo(evidence);
        assertThat(result.responseDueAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(result.responseWindowOpen()).isTrue();
        assertThat(result.targetName()).isEqualTo("Giáo trình Toán rời rạc");
    }

    @Test
    void detailForRespondent_notRespondent_throwsReportNotFound() {
        // given
        Report report = report(7L, TargetType.USER, null, 20L, null);
        report.awaitResponse(NOW.plusSeconds(3600));
        when(reportRepository.findById(7L)).thenReturn(Optional.of(report));

        // when, then
        assertThatThrownBy(() -> service.detailForRespondent(ME, 7L))
                .hasFieldOrPropertyWithValue("errorCode", ReportErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    void detailForRespondent_noDeadlineYet_throwsReportNotFound() {
        // given: Admin chưa yêu cầu giải trình nên hồ sơ chưa có hạn
        Report report = report(8L, TargetType.ITEM, 5L, ME, null);
        when(reportRepository.findById(8L)).thenReturn(Optional.of(report));

        // when, then
        assertThatThrownBy(() -> service.detailForRespondent(ME, 8L))
                .hasFieldOrPropertyWithValue("errorCode", ReportErrorCode.REPORT_NOT_FOUND);
    }
}
