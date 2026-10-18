package com.shareloop.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.ConcludeReportRequest;
import com.shareloop.report.dto.CreateReportRequest;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportServiceTest {

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final ItemQueryService itemQueryService = mock(ItemQueryService.class);
    private final ItemService itemService = mock(ItemService.class);
    private final ReputationService reputationService = mock(ReputationService.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final RequestService requestService = mock(RequestService.class);

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(
                reportRepository,
                itemQueryService,
                itemService,
                reputationService,
                activityLogService,
                configService,
                requestService);
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
    // Các ca kiểm thử cho Admin Conclude Report (Issue #113)
    // =========================================================================

    @Test
    @DisplayName("Admin kết luận DISMISS: chuyển status DISMISSED, không trừ sao, không ẩn bài, ghi log")
    void conclude_dismiss_success() {
        Long adminId = 1L;
        Long reportId = 10L;
        Report report = new Report(
                100L,
                TargetType.ITEM,
                200L,
                null,
                null,
                ReportReason.NOT_AS_DESCRIBED,
                "Mô tả không đúng",
                ReportStage.BEFORE);
        ReflectionTestUtils.setField(report, "id", reportId);

        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ConcludeReportRequest request = new ConcludeReportRequest(ReportDecision.DISMISS, "Không có cơ sở vi phạm");
        ReportConclusionResponse response = reportService.conclude(adminId, reportId, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(response.starsDeducted()).isEqualTo((short) 0);
        assertThat(response.resolvedBy()).isEqualTo(adminId);
        assertThat(response.resolution()).isEqualTo("Không có cơ sở vi phạm");
        assertThat(response.resolvedAt()).isNotNull();

        verify(reputationService, never()).deductStar(anyLong(), anyInt(), anyString());
        verify(itemService, never()).hide(anyLong());
        verify(itemService, never()).hideAllOfUser(anyLong());
        verify(activityLogService)
                .log(
                        eq("REPORT_DISMISSED"),
                        eq(adminId),
                        eq("REPORT"),
                        eq(reportId),
                        anyMap(),
                        eq("Không có cơ sở vi phạm"));
    }

    @Test
    @DisplayName("Admin kết luận UPHOLD với lý do thường: trừ 1 sao chủ bài, ẩn bài bị báo cáo, KHÔNG ẩn toàn bộ bài")
    void conclude_uphold_itemTarget_normalReason_success() {
        Long adminId = 1L;
        Long reportId = 11L;
        Long itemId = 201L;
        Long donorId = 301L;

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

        // Trừ 1 sao chủ bài
        verify(reputationService).deductStar(eq(donorId), eq(1), eq("Xác minh hàng không đúng mô tả"));
        // Ẩn bài đăng bị khiếu nại
        verify(itemService).hide(itemId);
        // Không phải lý do nghiêm trọng nên không gọi hideAllOfUser
        verify(itemService, never()).hideAllOfUser(anyLong());
        // Ghi activity log
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
        // Ẩn bài vi phạm
        verify(itemService).hide(itemId);
        // BR-A07: Ẩn tất cả bài khác của người vi phạm
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
