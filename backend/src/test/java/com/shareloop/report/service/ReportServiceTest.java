package com.shareloop.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.CreateReportRequest;
import com.shareloop.report.dto.ReportResponse;
import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportServiceTest {

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final ItemQueryService itemQueryService = mock(ItemQueryService.class);
    private final ItemService itemService = mock(ItemService.class);

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, itemQueryService, itemService);
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
        assertThat(response.description()).isEqualTo("Quấy rối qua tin nhắn");
    }

    @Test
    @DisplayName("Tự báo cáo tài khoản của chính mình ném lỗi REPORT_CANNOT_REPORT_SELF")
    void createReport_selfReportUser_throwsException() {
        Long reporterId = 10L;
        CreateReportRequest request = new CreateReportRequest(
                TargetType.USER, reporterId, null, null, null, ReportReason.HARASSMENT, "Tự báo cáo");

        assertThatThrownBy(() -> reportService.createReport(reporterId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_CANNOT_REPORT_SELF));
    }

    @Test
    @DisplayName("Tự báo cáo bài đăng của chính mình ném lỗi REPORT_CANNOT_REPORT_SELF")
    void createReport_selfReportItem_throwsException() {
        Long reporterId = 10L;
        Long itemId = 100L;

        CreateReportRequest request = new CreateReportRequest(
                TargetType.ITEM, null, itemId, null, null, ReportReason.PROHIBITED_ITEM, "Đồ cấm");

        ItemSummary summary = new ItemSummary(itemId, reporterId, OfferType.GIVE, ItemStatus.APPROVED);
        when(itemQueryService.getSummary(itemId)).thenReturn(summary);

        assertThatThrownBy(() -> reportService.createReport(reporterId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_CANNOT_REPORT_SELF));
    }

    @Test
    @DisplayName("Báo cáo trùng bài đăng đã báo cáo ném lỗi REPORT_ALREADY_REPORTED")
    void createReport_alreadyReportedItem_throwsException() {
        Long reporterId = 10L;
        Long itemId = 100L;
        Long donorId = 20L;

        CreateReportRequest request = new CreateReportRequest(
                TargetType.ITEM, null, itemId, null, null, ReportReason.COUNTERFEIT, "Hàng giả");

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
    @DisplayName("Báo cáo trùng người dùng đã báo cáo ném lỗi REPORT_ALREADY_REPORTED")
    void createReport_alreadyReportedUser_throwsException() {
        Long reporterId = 10L;
        Long targetUserId = 20L;

        CreateReportRequest request = new CreateReportRequest(
                TargetType.USER, targetUserId, null, null, null, ReportReason.HARASSMENT, "Quấy rối");

        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetUserId(reporterId, TargetType.USER, targetUserId))
                .thenReturn(true);

        assertThatThrownBy(() -> reportService.createReport(reporterId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_ALREADY_REPORTED));
    }

    @Test
    @DisplayName("Báo cáo bài đăng không tồn tại ném lỗi REPORT_TARGET_NOT_FOUND")
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
}
