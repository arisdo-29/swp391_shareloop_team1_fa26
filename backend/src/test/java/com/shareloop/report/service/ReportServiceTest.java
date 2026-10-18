package com.shareloop.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.media.service.MediaService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.CreateDisputeRequest;
import com.shareloop.report.dto.CreateReportRequest;
import com.shareloop.report.dto.DisputeResponse;
import com.shareloop.report.dto.DisputeResponseRequest;
import com.shareloop.report.dto.ReportResponse;
import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.repository.ReportRepository;
import com.shareloop.setting.service.ConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportServiceTest {

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final ItemQueryService itemQueryService = mock(ItemQueryService.class);
    private final ItemService itemService = mock(ItemService.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final NotificationService notificationService = mock(NotificationService.class);

    private Clock clock;
    private ReportService reportService;

    private static final Instant BASE_TIME = Instant.parse("2026-10-18T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(BASE_TIME, ZoneId.of("UTC"));
        ReportService target = new ReportService(
                reportRepository,
                itemQueryService,
                itemService,
                mediaService,
                configService,
                notificationService,
                clock,
                null);
        reportService = spy(target);
    }

    @Test
    @DisplayName("Báo cáo bài đăng hợp lệ thành công")
    void createReport_itemTarget_success() {
        Long reporterId = 10L;
        Long itemId = 100L;
        Long donorId = 20L;

        CreateReportRequest request = new CreateReportRequest(
                TargetType.ITEM,
                null,
                itemId,
                null,
                null,
                ReportReason.NOT_AS_DESCRIBED,
                "Đồ rách nát không giống mô tả");

        ItemSummary summary = new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED);

        when(itemQueryService.getSummary(itemId)).thenReturn(summary);
        when(reportRepository.existsByReporterIdAndTargetTypeAndItemId(reporterId, TargetType.ITEM, itemId))
                .thenReturn(false);
        when(reportRepository.countByItemId(itemId)).thenReturn(1L);

        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        ReportResponse response = reportService.createReport(reporterId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.reporterId()).isEqualTo(reporterId);
        assertThat(response.targetType()).isEqualTo(TargetType.ITEM);
        assertThat(response.itemId()).isEqualTo(itemId);
        assertThat(response.reason()).isEqualTo(ReportReason.NOT_AS_DESCRIBED);
        assertThat(response.description()).isEqualTo("Đồ rách nát không giống mô tả");
        assertThat(response.status()).isEqualTo(ReportStatus.OPEN);

        verify(itemService, never()).hide(itemId);
    }

    @Test
    @DisplayName("Báo cáo người dùng hợp lệ thành công")
    void createReport_userTarget_success() {
        Long reporterId = 10L;
        Long targetUserId = 20L;

        CreateReportRequest request = new CreateReportRequest(
                TargetType.USER, targetUserId, null, null, null, ReportReason.HARASSMENT, "Quấy rối qua tin nhắn");

        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetUserId(reporterId, TargetType.USER, targetUserId))
                .thenReturn(false);

        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });

        ReportResponse response = reportService.createReport(reporterId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.targetType()).isEqualTo(TargetType.USER);
        assertThat(response.targetUserId()).isEqualTo(targetUserId);
        assertThat(response.reason()).isEqualTo(ReportReason.HARASSMENT);
    }

    @Test
    @DisplayName("Báo cáo yêu cầu giao dịch hợp lệ thành công")
    void createReport_requestTarget_success() {
        Long reporterId = 10L;
        Long requestId = 50L;

        CreateReportRequest request = new CreateReportRequest(
                TargetType.REQUEST, null, null, null, requestId, ReportReason.NO_SHOW, "Không đến nhận đồ");

        when(reportRepository.existsByReporterIdAndTargetTypeAndRequestId(reporterId, TargetType.REQUEST, requestId))
                .thenReturn(false);

        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 3L);
            return saved;
        });

        ReportResponse response = reportService.createReport(reporterId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.targetType()).isEqualTo(TargetType.REQUEST);
        assertThat(response.requestId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("Tự báo cáo bài của mình ném lỗi REPORT_CANNOT_REPORT_SELF")
    void createReport_selfItem_throwsException() {
        Long reporterId = 10L;
        Long itemId = 100L;

        CreateReportRequest request = new CreateReportRequest(
                TargetType.ITEM, null, itemId, null, null, ReportReason.OTHER, "Tự báo cáo mình");

        ItemSummary summary = new ItemSummary(itemId, reporterId, OfferType.GIVE, ItemStatus.APPROVED);

        when(itemQueryService.getSummary(itemId)).thenReturn(summary);

        assertThatThrownBy(() -> reportService.createReport(reporterId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_CANNOT_REPORT_SELF));
    }

    @Test
    @DisplayName("Tự báo cáo chính mình ném lỗi REPORT_CANNOT_REPORT_SELF")
    void createReport_selfUser_throwsException() {
        Long reporterId = 10L;

        CreateReportRequest request = new CreateReportRequest(
                TargetType.USER, reporterId, null, null, null, ReportReason.OTHER, "Tự báo cáo mình");

        assertThatThrownBy(() -> reportService.createReport(reporterId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_CANNOT_REPORT_SELF));
    }

    @Test
    @DisplayName("Báo cáo trùng lặp bài đăng ném lỗi REPORT_ALREADY_REPORTED")
    void createReport_alreadyReportedItem_throwsException() {
        Long reporterId = 10L;
        Long itemId = 100L;
        Long donorId = 20L;

        CreateReportRequest request =
                new CreateReportRequest(TargetType.ITEM, null, itemId, null, null, ReportReason.OTHER, "Báo cáo lần 2");

        ItemSummary summary = new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED);

        when(itemQueryService.getSummary(itemId)).thenReturn(summary);
        when(reportRepository.existsByReporterIdAndTargetTypeAndItemId(reporterId, TargetType.ITEM, itemId))
                .thenReturn(true);

        assertThatThrownBy(() -> reportService.createReport(reporterId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_ALREADY_REPORTED));
    }

    @Test
    @DisplayName("Báo cáo bài không tồn tại ném lỗi REPORT_TARGET_NOT_FOUND")
    void createReport_itemNotFound_throwsException() {
        Long reporterId = 10L;
        Long itemId = 999L;

        CreateReportRequest request =
                new CreateReportRequest(TargetType.ITEM, null, itemId, null, null, ReportReason.OTHER, "Bài ảo");

        when(itemQueryService.getSummary(itemId)).thenReturn(null);

        assertThatThrownBy(() -> reportService.createReport(reporterId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_TARGET_NOT_FOUND));
    }

    @Test
    @DisplayName("BR-A03: Khi số lượng báo cáo bài đăng đạt ngưỡng >= 3 thì tự động ẩn bài")
    void createReport_reachesThreshold_hidesItem() {
        Long reporterId = 10L;
        Long itemId = 100L;
        Long donorId = 20L;

        CreateReportRequest request = new CreateReportRequest(
                TargetType.ITEM, itemId, null, null, null, ReportReason.PROHIBITED_ITEM, "Đồ cấm nguy hiểm");

        ItemSummary summary = new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED);

        when(itemQueryService.getSummary(itemId)).thenReturn(summary);
        when(reportRepository.existsByReporterIdAndTargetTypeAndItemId(reporterId, TargetType.ITEM, itemId))
                .thenReturn(false);
        when(reportRepository.countByItemId(itemId)).thenReturn(3L);

        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });

        reportService.createReport(reporterId, request);

        verify(itemService).hide(itemId);
    }

    @Test
    @DisplayName("Thiếu targetId/itemId ném lỗi REPORT_TARGET_REQUIRED")
    void createReport_missingTargetId_throwsException() {
        Long reporterId = 10L;
        CreateReportRequest request =
                new CreateReportRequest(TargetType.ITEM, null, null, null, null, ReportReason.OTHER, "Không có id");

        assertThatThrownBy(() -> reportService.createReport(reporterId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_TARGET_REQUIRED));
    }

    // =========================================================================
    // Các ca kiểm thử cho Khiếu nại giao dịch & Giải trình (Issue #112)
    // =========================================================================

    @Test
    @DisplayName("Khiếu nại vào ngày thứ 6 sau Completed: thành công, mở 3 ngày giải trình, báo bên bị khiếu nại")
    void openDispute_day6AfterCompleted_success() {
        Long receiverId = 10L;
        Long donorId = 20L;
        Long requestId = 100L;
        Long itemId = 200L;

        // Completed cách đây 6 ngày
        Instant completedAt = BASE_TIME.minus(Duration.ofDays(6));
        ReportService.RequestSnapshot snapshot =
                new ReportService.RequestSnapshot(requestId, itemId, receiverId, "COMPLETED", completedAt);
        doReturn(snapshot).when(reportService).findRequest(requestId);

        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED));
        when(reportRepository.existsByReporterIdAndTargetTypeAndRequestId(receiverId, TargetType.REQUEST, requestId))
                .thenReturn(false);

        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        CreateDisputeRequest request =
                new CreateDisputeRequest(ReportReason.NOT_AS_DESCRIBED, "Áo bị rách", List.of(50L, 51L));

        DisputeResponse response = reportService.openDispute(receiverId, requestId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.responseDueAt()).isEqualTo(BASE_TIME.plus(Duration.ofDays(3)));

        // Kiểm tra attach bằng chứng
        verify(mediaService).attach(List.of(50L, 51L), receiverId);
        // Kiểm tra gửi thông báo cho bên bị khiếu nại (donorId)
        verify(notificationService).notify(eq(donorId), eq("DISPUTE_OPENED"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Khiếu nại đúng mốc 7 ngày (7 ngày 0 giây): còn trong hạn nên thành công")
    void openDispute_exactly7Days_success() {
        Long receiverId = 10L;
        Long donorId = 20L;
        Long requestId = 101L;
        Long itemId = 201L;

        Instant completedAt = BASE_TIME.minus(Duration.ofDays(7));
        ReportService.RequestSnapshot snapshot =
                new ReportService.RequestSnapshot(requestId, itemId, receiverId, "COMPLETED", completedAt);
        doReturn(snapshot).when(reportService).findRequest(requestId);

        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED));
        when(reportRepository.existsByReporterIdAndTargetTypeAndRequestId(receiverId, TargetType.REQUEST, requestId))
                .thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });

        CreateDisputeRequest request = new CreateDisputeRequest(ReportReason.COUNTERFEIT, "Hàng giả", null);
        DisputeResponse response = reportService.openDispute(receiverId, requestId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Khiếu nại sau 7 ngày + 1 giây: ném lỗi DISPUTE_WINDOW_EXPIRED (422)")
    void openDispute_7DaysAnd1Second_throwsWindowExpired() {
        Long receiverId = 10L;
        Long donorId = 20L;
        Long requestId = 102L;
        Long itemId = 202L;

        Instant completedAt = BASE_TIME.minus(Duration.ofDays(7)).minusSeconds(1);
        ReportService.RequestSnapshot snapshot =
                new ReportService.RequestSnapshot(requestId, itemId, receiverId, "COMPLETED", completedAt);
        doReturn(snapshot).when(reportService).findRequest(requestId);

        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED));

        CreateDisputeRequest request = new CreateDisputeRequest(ReportReason.NOT_AS_DESCRIBED, "Quá hạn", null);

        assertThatThrownBy(() -> reportService.openDispute(receiverId, requestId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.DISPUTE_WINDOW_EXPIRED));
    }

    @Test
    @DisplayName("Giao dịch chưa COMPLETED (ví dụ LOGISTICS_CONFIRMED) ném lỗi DISPUTE_REQUEST_NOT_COMPLETED (409)")
    void openDispute_requestNotCompleted_throwsException() {
        Long receiverId = 10L;
        Long donorId = 20L;
        Long requestId = 103L;
        Long itemId = 203L;

        ReportService.RequestSnapshot snapshot =
                new ReportService.RequestSnapshot(requestId, itemId, receiverId, "LOGISTICS_CONFIRMED", null);
        doReturn(snapshot).when(reportService).findRequest(requestId);

        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED));

        CreateDisputeRequest request = new CreateDisputeRequest(ReportReason.NOT_AS_DESCRIBED, "Chưa xong", null);

        assertThatThrownBy(() -> reportService.openDispute(receiverId, requestId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.DISPUTE_REQUEST_NOT_COMPLETED));
    }

    @Test
    @DisplayName("Người ngoài giao dịch khiếu nại ném lỗi DISPUTE_NOT_A_PARTY (403)")
    void openDispute_notParty_throwsException() {
        Long outsiderId = 999L;
        Long receiverId = 10L;
        Long donorId = 20L;
        Long requestId = 104L;
        Long itemId = 204L;

        ReportService.RequestSnapshot snapshot =
                new ReportService.RequestSnapshot(requestId, itemId, receiverId, "COMPLETED", BASE_TIME);
        doReturn(snapshot).when(reportService).findRequest(requestId);

        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED));

        CreateDisputeRequest request = new CreateDisputeRequest(ReportReason.NOT_AS_DESCRIBED, "Người ngoài", null);

        assertThatThrownBy(() -> reportService.openDispute(outsiderId, requestId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.DISPUTE_NOT_A_PARTY));
    }

    @Test
    @DisplayName("Khiếu nại lần 2 cùng một giao dịch ném lỗi REPORT_ALREADY_REPORTED (409)")
    void openDispute_alreadyReported_throwsException() {
        Long receiverId = 10L;
        Long donorId = 20L;
        Long requestId = 105L;
        Long itemId = 205L;

        ReportService.RequestSnapshot snapshot =
                new ReportService.RequestSnapshot(requestId, itemId, receiverId, "COMPLETED", BASE_TIME);
        doReturn(snapshot).when(reportService).findRequest(requestId);

        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED));
        when(reportRepository.existsByReporterIdAndTargetTypeAndRequestId(receiverId, TargetType.REQUEST, requestId))
                .thenReturn(true);

        CreateDisputeRequest request = new CreateDisputeRequest(ReportReason.NOT_AS_DESCRIBED, "Lần 2", null);

        assertThatThrownBy(() -> reportService.openDispute(receiverId, requestId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_ALREADY_REPORTED));
    }

    @Test
    @DisplayName("Bên bị khiếu nại giải trình trong vòng 3 ngày kèm bằng chứng: thành công")
    void respondDispute_within3Days_success() {
        Long respondentId = 20L;
        Long disputeId = 1L;

        Report dispute = Report.createDispute(
                10L,
                100L,
                200L,
                respondentId,
                ReportReason.NOT_AS_DESCRIBED,
                "Áo rách",
                "[50]",
                BASE_TIME.plus(Duration.ofDays(3)));
        ReflectionTestUtils.setField(dispute, "id", disputeId);

        when(reportRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeResponseRequest request = new DisputeResponseRequest("Áo nguyên vẹn", List.of(60L));

        reportService.respondDispute(respondentId, disputeId, request);

        assertThat(dispute.getResponse()).isEqualTo("Áo nguyên vẹn");
        assertThat(dispute.getRespondedAt()).isEqualTo(BASE_TIME);
        assertThat(dispute.getResponseEvidence()).isEqualTo("[60]");

        verify(mediaService).attach(List.of(60L), respondentId);
    }

    @Test
    @DisplayName("Giải trình sau 3 ngày hết hạn ném lỗi DISPUTE_RESPONSE_WINDOW_EXPIRED (422)")
    void respondDispute_after3Days_throwsExpired() {
        Long respondentId = 20L;
        Long disputeId = 2L;

        // Hạn chót là 1 tiếng trước BASE_TIME
        Instant pastDue = BASE_TIME.minus(Duration.ofHours(1));
        Report dispute = Report.createDispute(
                10L, 100L, 200L, respondentId, ReportReason.NOT_AS_DESCRIBED, "Áo rách", null, pastDue);
        ReflectionTestUtils.setField(dispute, "id", disputeId);

        when(reportRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));

        DisputeResponseRequest request = new DisputeResponseRequest("Giải trình trễ", null);

        assertThatThrownBy(() -> reportService.respondDispute(respondentId, disputeId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.DISPUTE_RESPONSE_WINDOW_EXPIRED));
    }

    @Test
    @DisplayName("Người không phải bên bị khiếu nại (bên khiếu nại tự giải trình) ném DISPUTE_NOT_RESPONDENT (403)")
    void respondDispute_notRespondent_throwsException() {
        Long reporterId = 10L;
        Long respondentId = 20L;
        Long disputeId = 3L;

        Report dispute = Report.createDispute(
                reporterId,
                100L,
                200L,
                respondentId,
                ReportReason.NOT_AS_DESCRIBED,
                "Áo rách",
                null,
                BASE_TIME.plus(Duration.ofDays(3)));
        ReflectionTestUtils.setField(dispute, "id", disputeId);

        when(reportRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));

        DisputeResponseRequest request = new DisputeResponseRequest("Người khiếu nại giải trình", null);

        // reporterId tự gọi respondDispute
        assertThatThrownBy(() -> reportService.respondDispute(reporterId, disputeId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.DISPUTE_NOT_RESPONDENT));
    }

    @Test
    @DisplayName("Giải trình lần 2 khi đã gửi bản giải trình trước đó ném DISPUTE_ALREADY_RESPONDED (409)")
    void respondDispute_alreadyResponded_throwsException() {
        Long respondentId = 20L;
        Long disputeId = 4L;

        Report dispute = Report.createDispute(
                10L,
                100L,
                200L,
                respondentId,
                ReportReason.NOT_AS_DESCRIBED,
                "Áo rách",
                null,
                BASE_TIME.plus(Duration.ofDays(3)));
        ReflectionTestUtils.setField(dispute, "id", disputeId);
        ReflectionTestUtils.setField(dispute, "respondedAt", BASE_TIME.minus(Duration.ofHours(2)));

        when(reportRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));

        DisputeResponseRequest request = new DisputeResponseRequest("Gửi lần 2", null);

        assertThatThrownBy(() -> reportService.respondDispute(respondentId, disputeId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.DISPUTE_ALREADY_RESPONDED));
    }

    @Test
    @DisplayName("Giải trình khiếu nại đã được Admin kết luận (RESOLVED) ném DISPUTE_ALREADY_CONCLUDED (409)")
    void respondDispute_alreadyConcluded_throwsException() {
        Long respondentId = 20L;
        Long disputeId = 5L;

        Report dispute = Report.createDispute(
                10L,
                100L,
                200L,
                respondentId,
                ReportReason.NOT_AS_DESCRIBED,
                "Áo rách",
                null,
                BASE_TIME.plus(Duration.ofDays(3)));
        ReflectionTestUtils.setField(dispute, "id", disputeId);
        ReflectionTestUtils.setField(dispute, "status", ReportStatus.RESOLVED);

        when(reportRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));

        DisputeResponseRequest request = new DisputeResponseRequest("Gửi khi đã kết luận", null);

        assertThatThrownBy(() -> reportService.respondDispute(respondentId, disputeId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.DISPUTE_ALREADY_CONCLUDED));
    }
}
