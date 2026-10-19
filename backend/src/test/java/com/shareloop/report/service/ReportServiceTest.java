package com.shareloop.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.media.service.MediaService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.ConcludeReportRequest;
import com.shareloop.report.dto.CreateDisputeRequest;
import com.shareloop.report.dto.CreateReportRequest;
import com.shareloop.report.dto.DisputeResponse;
import com.shareloop.report.dto.DisputeResponseRequest;
import com.shareloop.report.dto.ReportConclusionResponse;
import com.shareloop.report.dto.ReportDecision;
import com.shareloop.report.dto.ReportResponse;
import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStage;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.repository.ReportRepository;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.dto.RequestChatContext;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.service.RequestService;
import com.shareloop.setting.service.ConfigService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-10-18T10:00:00Z");

    @Mock
    ReportRepository reportRepository;

    @Mock
    ItemQueryService itemQueryService;

    @Mock
    ItemService itemService;

    @Mock
    MediaService mediaService;

    @Mock
    NotificationService notificationService;

    @Mock
    ReputationService reputationService;

    @Mock
    ActivityLogService activityLogService;

    @Mock
    ConfigService configService;

    @Mock
    RequestService requestService;

    @Mock
    JdbcClient jdbcClient;

    Clock clock = Clock.fixed(BASE_TIME, ZoneId.of("UTC"));

    ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = spy(new ReportService(
                reportRepository,
                itemQueryService,
                itemService,
                mediaService,
                notificationService,
                reputationService,
                activityLogService,
                configService,
                requestService,
                jdbcClient,
                clock));
    }

    @Test
    @DisplayName("Tạo report ITEM thành công khi dữ liệu hợp lệ")
    void createReport_item_success() {
        Long reporterId = 1L;
        Long itemId = 10L;
        Long donorId = 2L;

        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED));
        when(reportRepository.existsByReporterIdAndTargetTypeAndItemId(reporterId, TargetType.ITEM, itemId))
                .thenReturn(false);
        when(reportRepository.countByItemId(itemId)).thenReturn(1L);
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateReportRequest req = new CreateReportRequest(
                TargetType.ITEM, itemId, null, null, null, ReportReason.PROHIBITED_ITEM, "Hàng cấm");

        ReportResponse res = reportService.create(reporterId, req);

        assertThat(res.targetType()).isEqualTo(TargetType.ITEM);
        assertThat(res.stage()).isEqualTo(ReportStage.BEFORE);
        assertThat(res.status()).isEqualTo(ReportStatus.OPEN);
        verify(reportRepository).save(any(Report.class));
        verify(itemService, never()).hide(anyLong());
    }

    @Test
    @DisplayName("Không được tự report bài đăng của chính mình")
    void reportItem_selfReport_throwsException() {
        Long userId = 1L;
        Long itemId = 10L;

        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, userId, OfferType.GIVE, ItemStatus.APPROVED));

        CreateReportRequest req = new CreateReportRequest(
                TargetType.ITEM, itemId, null, null, null, ReportReason.PROHIBITED_ITEM, "Tự report");

        assertThatThrownBy(() -> reportService.create(userId, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_CANNOT_REPORT_SELF));
    }

    @Test
    @DisplayName("Không được tự report chính mình khi targetType là USER")
    void reportUser_selfReport_throwsException() {
        Long userId = 1L;

        CreateReportRequest req = new CreateReportRequest(
                TargetType.USER, userId, null, null, null, ReportReason.HARASSMENT, "Tự report user");

        assertThatThrownBy(() -> reportService.create(userId, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_CANNOT_REPORT_SELF));
    }

    @Test
    @DisplayName("Report cùng một bài đăng lần 2 thì ném REPORT_ALREADY_REPORTED")
    void reportItem_alreadyReported_throwsException() {
        Long reporterId = 1L;
        Long itemId = 10L;
        Long donorId = 2L;

        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED));
        when(reportRepository.existsByReporterIdAndTargetTypeAndItemId(reporterId, TargetType.ITEM, itemId))
                .thenReturn(true);

        CreateReportRequest req = new CreateReportRequest(
                TargetType.ITEM, itemId, null, null, null, ReportReason.PROHIBITED_ITEM, "Report lại");

        assertThatThrownBy(() -> reportService.create(reporterId, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_ALREADY_REPORTED));
    }

    @Test
    @DisplayName("BR-A03: Khi số lượng report bài đăng >= 3 thì tự động ẩn bài")
    void reportItem_countReached3_callsItemServiceHide() {
        Long reporterId = 1L;
        Long itemId = 10L;
        Long donorId = 2L;

        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED));
        when(reportRepository.existsByReporterIdAndTargetTypeAndItemId(reporterId, TargetType.ITEM, itemId))
                .thenReturn(false);
        when(reportRepository.countByItemId(itemId)).thenReturn(3L);
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateReportRequest req = new CreateReportRequest(
                TargetType.ITEM, itemId, null, null, null, ReportReason.PROHIBITED_ITEM, "Vi phạm lần 3");

        reportService.create(reporterId, req);

        verify(itemService).hide(itemId);
    }

    // ==================== DISPUTE TESTS (#112) ====================

    @Test
    @DisplayName("Mở khiếu nại giao dịch đã COMPLETED trong vòng 7 ngày: thành công và mở cửa sổ giải trình 3 ngày")
    void openDispute_completedWithin7Days_success() {
        Long receiverId = 10L;
        Long donorId = 20L;
        Long requestId = 100L;
        Long itemId = 200L;

        ReportService.RequestSnapshot snapshot = new ReportService.RequestSnapshot(
                requestId, itemId, receiverId, "COMPLETED", BASE_TIME.minus(Duration.ofDays(2)));
        doReturn(snapshot).when(reportService).findRequest(requestId);

        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED));
        when(reportRepository.existsByReporterIdAndTargetTypeAndRequestId(receiverId, TargetType.REQUEST, requestId))
                .thenReturn(false);
        when(configService.getDecimal("DISPUTE_WINDOW_DAYS")).thenReturn(new BigDecimal("7"));
        when(configService.getDecimal("DISPUTE_RESPONSE_DAYS")).thenReturn(new BigDecimal("3"));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 1L);
            return r;
        });

        CreateDisputeRequest request =
                new CreateDisputeRequest(ReportReason.NOT_AS_DESCRIBED, "Hàng bị lỗi không như mô tả", List.of(50L));

        DisputeResponse response = reportService.openDispute(receiverId, requestId, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.responseDueAt()).isEqualTo(BASE_TIME.plus(Duration.ofDays(3)));

        verify(mediaService).attach(List.of(50L), receiverId);
        verify(notificationService)
                .notify(
                        eq(donorId),
                        eq("DISPUTE_OPENED"),
                        eq("Giao dịch #100 bị khiếu nại"),
                        any(String.class),
                        eq("/me/disputes/1"));
    }

    @Test
    @DisplayName("Khiếu nại giao dịch chưa COMPLETED ném lỗi DISPUTE_REQUEST_NOT_COMPLETED (409)")
    void openDispute_notCompleted_throwsException() {
        Long receiverId = 10L;
        Long donorId = 20L;
        Long requestId = 101L;
        Long itemId = 201L;

        ReportService.RequestSnapshot snapshot =
                new ReportService.RequestSnapshot(requestId, itemId, receiverId, "CONFIRMED", null);
        doReturn(snapshot).when(reportService).findRequest(requestId);

        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED));

        CreateDisputeRequest request = new CreateDisputeRequest(ReportReason.NOT_AS_DESCRIBED, "Chưa nhận đồ", null);

        assertThatThrownBy(() -> reportService.openDispute(receiverId, requestId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.DISPUTE_REQUEST_NOT_COMPLETED));
    }

    @Test
    @DisplayName("Khiếu nại sau 7 ngày ném lỗi DISPUTE_WINDOW_EXPIRED (422)")
    void openDispute_after7Days_throwsException() {
        Long receiverId = 10L;
        Long donorId = 20L;
        Long requestId = 102L;
        Long itemId = 202L;

        Instant completedAt = BASE_TIME.minus(Duration.ofDays(8));
        ReportService.RequestSnapshot snapshot =
                new ReportService.RequestSnapshot(requestId, itemId, receiverId, "COMPLETED", completedAt);
        doReturn(snapshot).when(reportService).findRequest(requestId);

        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED));
        when(configService.getDecimal("DISPUTE_WINDOW_DAYS")).thenReturn(new BigDecimal("7"));

        CreateDisputeRequest request = new CreateDisputeRequest(ReportReason.NOT_AS_DESCRIBED, "Quá hạn", null);

        assertThatThrownBy(() -> reportService.openDispute(receiverId, requestId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.DISPUTE_WINDOW_EXPIRED));
    }

    @Test
    @DisplayName("Người dùng ngoài giao dịch khiếu nại ném DISPUTE_NOT_A_PARTY (403)")
    void openDispute_outsider_throwsForbidden() {
        Long receiverId = 10L;
        Long donorId = 20L;
        Long outsiderId = 99L;
        Long requestId = 103L;
        Long itemId = 203L;

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
    @DisplayName("Người không phải bên bị khiếu nại ném DISPUTE_NOT_RESPONDENT (403)")
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

    // ==================== CONCLUSION TESTS (#113) ====================

    @Test
    @DisplayName("Admin kết luận DISMISS: cập nhật DISMISSED, không trừ sao, không ẩn bài, có ghi activity log")
    void conclude_dismiss_success() {
        Long adminId = 1L;
        Long reportId = 10L;

        Report report = new Report(
                100L, TargetType.USER, null, 200L, null, ReportReason.HARASSMENT, "Báo cáo bừa", ReportStage.BEFORE);
        ReflectionTestUtils.setField(report, "id", reportId);

        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ConcludeReportRequest request = new ConcludeReportRequest(ReportDecision.DISMISS, "Không đủ bằng chứng");
        ReportConclusionResponse response = reportService.conclude(adminId, reportId, request);

        assertThat(response.status()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(response.starsDeducted()).isEqualTo((short) 0);
        assertThat(response.resolvedBy()).isEqualTo(adminId);

        verify(reputationService, never()).deductStar(anyLong(), any(Integer.class), any());
        verify(itemService, never()).hide(anyLong());
        verify(activityLogService)
                .log(
                        eq("REPORT_DISMISSED"),
                        eq(adminId),
                        eq("REPORT"),
                        eq(reportId),
                        anyMap(),
                        eq("Không đủ bằng chứng"));
    }

    @Test
    @DisplayName(
            "Admin kết luận UPHOLD cho bài ITEM thông thường: trừ 1 sao chủ bài, ẩn bài đó, không ẩn toàn bộ bài khác")
    void conclude_uphold_itemTarget_success() {
        Long adminId = 1L;
        Long reportId = 11L;
        Long itemId = 105L;
        Long donorId = 205L;

        Report report = new Report(
                100L,
                TargetType.ITEM,
                itemId,
                null,
                null,
                ReportReason.NOT_AS_DESCRIBED,
                "Đồ cũ nát",
                ReportStage.BEFORE);
        ReflectionTestUtils.setField(report, "id", reportId);

        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ConcludeReportRequest request =
                new ConcludeReportRequest(ReportDecision.UPHOLD, "Xác minh hàng không đúng mô tả");
        ReportConclusionResponse response = reportService.conclude(adminId, reportId, request);

        assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(response.starsDeducted()).isEqualTo((short) 1);
        assertThat(response.resolvedBy()).isEqualTo(adminId);

        verify(reputationService).deductStar(eq(donorId), eq(1), eq("Xác minh hàng không đúng mô tả"));
        verify(itemService).hide(itemId);
        verify(itemService, never()).hideAllOfUser(anyLong());
        verify(activityLogService)
                .log(
                        eq("REPORT_UPHELD"),
                        eq(adminId),
                        eq("REPORT"),
                        eq(reportId),
                        anyMap(),
                        eq("Xác minh hàng không đúng mô tả"));
    }

    @Test
    @DisplayName(
            "Admin kết luận UPHOLD với lý do nghiêm trọng (PROHIBITED_ITEM): trừ 1 sao và ẩn TOÀN BỘ bài khác của người vi phạm (BR-A07)")
    void conclude_uphold_seriousReason_hidesAllOfUser() {
        Long adminId = 1L;
        Long reportId = 12L;
        Long itemId = 202L;
        Long donorId = 302L;

        Report report = new Report(
                100L,
                TargetType.ITEM,
                itemId,
                null,
                null,
                ReportReason.PROHIBITED_ITEM,
                "Bán vũ khí",
                ReportStage.BEFORE);
        ReflectionTestUtils.setField(report, "id", reportId);

        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(itemQueryService.getSummary(itemId))
                .thenReturn(new ItemSummary(itemId, donorId, OfferType.GIVE, ItemStatus.APPROVED));
        when(configService.getRaw("REPORT_SERIOUS_REASONS")).thenReturn("PROHIBITED_ITEM,COUNTERFEIT");
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ConcludeReportRequest request = new ConcludeReportRequest(ReportDecision.UPHOLD, "Vi phạm hàng cấm");
        ReportConclusionResponse response = reportService.conclude(adminId, reportId, request);

        assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(response.starsDeducted()).isEqualTo((short) 1);

        verify(reputationService).deductStar(eq(donorId), eq(1), eq("Vi phạm hàng cấm"));
        verify(itemService).hide(itemId);
        verify(itemService).hideAllOfUser(donorId);
    }

    @Test
    @DisplayName("Admin kết luận UPHOLD khi target là USER: trừ sao đúng targetUserId")
    void conclude_uphold_userTarget_success() {
        Long adminId = 1L;
        Long reportId = 13L;
        Long targetUserId = 401L;

        Report report = new Report(
                100L,
                TargetType.USER,
                null,
                targetUserId,
                null,
                ReportReason.HARASSMENT,
                "Quấy rối",
                ReportStage.BEFORE);
        ReflectionTestUtils.setField(report, "id", reportId);

        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ConcludeReportRequest request =
                new ConcludeReportRequest(ReportDecision.UPHOLD, "Xác minh có hành vi quấy rối");
        ReportConclusionResponse response = reportService.conclude(adminId, reportId, request);

        assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
        verify(reputationService).deductStar(eq(targetUserId), eq(1), eq("Xác minh có hành vi quấy rối"));
    }

    @Test
    @DisplayName("Admin kết luận UPHOLD khi target là REQUEST: xác định người bị khiếu nại qua RequestChatContext")
    void conclude_uphold_requestTarget_success() {
        Long adminId = 1L;
        Long reportId = 14L;
        Long requestId = 501L;
        Long reporterId = 100L;
        Long violatorId = 601L;

        Report report = new Report(
                reporterId,
                TargetType.REQUEST,
                null,
                null,
                requestId,
                ReportReason.NO_SHOW,
                "Bùng hẹn",
                ReportStage.BEFORE);
        ReflectionTestUtils.setField(report, "id", reportId);

        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(requestService.getChatContext(requestId))
                .thenReturn(
                        new RequestChatContext(requestId, RequestStatus.COMPLETED, reporterId, violatorId, 1L, null));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ConcludeReportRequest request = new ConcludeReportRequest(ReportDecision.UPHOLD, "Bùng hẹn không lý do");
        ReportConclusionResponse response = reportService.conclude(adminId, reportId, request);

        assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
        verify(reputationService).deductStar(eq(violatorId), eq(1), eq("Bùng hẹn không lý do"));
    }

    @Test
    @DisplayName("Kết luận báo cáo không tồn tại ném REPORT_NOT_FOUND")
    void conclude_notFound_throwsException() {
        Long adminId = 1L;
        Long reportId = 999L;

        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.empty());

        ConcludeReportRequest request = new ConcludeReportRequest(ReportDecision.UPHOLD, "Note");

        assertThatThrownBy(() -> reportService.conclude(adminId, reportId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_NOT_FOUND));
    }

    @Test
    @DisplayName("Kết luận báo cáo đã RESOLVED hoặc DISMISSED ném REPORT_ALREADY_CONCLUDED")
    void conclude_alreadyConcluded_throwsException() {
        Long adminId = 1L;
        Long reportId = 15L;

        Report report = new Report(
                100L, TargetType.USER, null, 200L, null, ReportReason.HARASSMENT, "Quấy rối", ReportStage.BEFORE);
        ReflectionTestUtils.setField(report, "id", reportId);
        ReflectionTestUtils.setField(report, "status", ReportStatus.RESOLVED);

        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));

        ConcludeReportRequest request = new ConcludeReportRequest(ReportDecision.UPHOLD, "Note");

        assertThatThrownBy(() -> reportService.conclude(adminId, reportId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_ALREADY_CONCLUDED));
    }
}
