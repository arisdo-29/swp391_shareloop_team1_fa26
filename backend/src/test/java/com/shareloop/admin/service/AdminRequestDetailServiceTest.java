package com.shareloop.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.admin.dto.AdminCancelRequest;
import com.shareloop.admin.dto.AdminRequestItemInfo;
import com.shareloop.admin.dto.AdminReschedule;
import com.shareloop.admin.dto.AdminShippingLeg;
import com.shareloop.admin.dto.AdminTimelineEvent;
import com.shareloop.request.dto.AdminRequestCore;
import com.shareloop.request.dto.CancelRequestInfo;
import com.shareloop.request.dto.RequestDeadlines;
import com.shareloop.request.dto.RescheduleInfo;
import com.shareloop.request.dto.ShippingInfo;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Dòng thời gian của giao dịch: thứ tự, nhãn người huỷ theo tiền tố lý do huỷ, giao dịch tự hoàn tất. */
class AdminRequestDetailServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-01T03:00:00Z");

    private AdminRequestCore core(String status, boolean autoConfirmed, String cancelReason) {
        return new AdminRequestCore(
                1L,
                10L,
                20L,
                null,
                "GIVE",
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                autoConfirmed,
                T0.plusSeconds(60),
                T0.plusSeconds(600),
                cancelReason,
                T0,
                T0.plusSeconds(300));
    }

    @ParameterizedTest(name = "{0} thì ghi {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "[Chủ bài] Hết hàng|Do chủ bài huỷ|Hết hàng",
                "[Người nhận] Không cần nữa|Do người nhận huỷ|Không cần nữa",
                "[Admin] Giao dịch bị kẹt|Do Admin huỷ|Giao dịch bị kẹt",
                "[Hệ thống] Quá hạn|Do hệ thống huỷ|Quá hạn"
            })
    void buildTimeline_whenCancelled_labelsWhoCancelledFromReasonPrefix(String reason, String label, String note) {
        // when
        List<AdminTimelineEvent> events = AdminRequestDetailService.buildTimeline(core("CANCELLED", false, reason));

        // then: mốc huỷ ở cuối, đúng người huỷ, lý do đã bỏ tiền tố
        AdminTimelineEvent last = events.get(events.size() - 1);
        assertThat(last.label()).isEqualTo(label);
        assertThat(last.note()).isEqualTo(note);
    }

    @Test
    @DisplayName("Huỷ không có tiền tố (dữ liệu cũ) thì ghi chung là giao dịch bị huỷ")
    void buildTimeline_whenCancelReasonHasNoPrefix_usesGenericLabel() {
        List<AdminTimelineEvent> events = AdminRequestDetailService.buildTimeline(core("CANCELLED", false, "Lý do cũ"));

        assertThat(events.get(events.size() - 1).label()).isEqualTo("Giao dịch bị huỷ");
        assertThat(events.get(events.size() - 1).note()).isEqualTo("Lý do cũ");
    }

    @Test
    void buildTimeline_whenAutoConfirmed_saysTransactionCompletedAutomatically() {
        List<AdminTimelineEvent> events = AdminRequestDetailService.buildTimeline(core("COMPLETED", true, null));

        assertThat(events).extracting(AdminTimelineEvent::label).contains("Giao dịch tự hoàn tất");
        assertThat(events).extracting(AdminTimelineEvent::label).doesNotContain("Giao dịch hoàn tất");
    }

    @Test
    void buildTimeline_isSortedByTimeAndSkipsMissingMilestones() {
        List<AdminTimelineEvent> events = AdminRequestDetailService.buildTimeline(core("COMPLETED", false, null));

        assertThat(events)
                .extracting(AdminTimelineEvent::label)
                .containsExactly("Người nhận gửi yêu cầu", "Chủ bài chọn người nhận", "Giao dịch hoàn tất");
        assertThat(events).extracting(AdminTimelineEvent::at).isSorted();
    }

    @Test
    void buildTimeline_whenRejected_showsOwnerChoseSomeoneElse() {
        List<AdminTimelineEvent> events = AdminRequestDetailService.buildTimeline(core("REJECTED", false, null));

        assertThat(events.get(events.size() - 1).label()).isEqualTo("Chủ bài chọn người nhận khác");
    }

    // Giao dịch Trao đổi qua Giao hàng: người nhận id 20, chủ bài id 99
    private AdminRequestCore swapShippingCore(
            String status, CancelRequestInfo cancel, RescheduleInfo reschedule, Instant donorShippedAt) {
        return new AdminRequestCore(
                1L,
                10L,
                20L,
                30L,
                "SWAP",
                status,
                "SHIPPING",
                null,
                T0.plusSeconds(3600),
                null,
                null,
                null,
                null,
                T0.plusSeconds(120),
                null,
                T0.plusSeconds(900),
                false,
                T0.plusSeconds(60),
                null,
                null,
                T0,
                T0.plusSeconds(300),
                cancel,
                reschedule,
                new ShippingInfo("12 Lê Lợi", "34 Trần Phú", donorShippedAt, null, true, true),
                new RequestDeadlines(null, null, T0.plusSeconds(7200), T0.plusSeconds(8000), null, null, null));
    }

    @Test
    @DisplayName("Đề nghị huỷ đang chờ hiện trạng thái chờ, ai đề nghị, lý do và hạn phản hồi")
    void toCancelRequest_whenPending_showsPendingWithExpiry() {
        AdminRequestCore core = swapShippingCore(
                "LOGISTICS_CONFIRMED",
                new CancelRequestInfo(99L, T0.plusSeconds(1800), "BUSY", "Tôi bận đột xuất", "Đi công tác"),
                RescheduleInfo.EMPTY,
                null);

        AdminCancelRequest result = AdminRequestDetailService.toCancelRequest(core);

        assertThat(result.state()).isEqualTo("PENDING");
        assertThat(result.requestedByRole()).isEqualTo("Chủ bài");
        assertThat(result.label()).isEqualTo("Tôi bận đột xuất");
        assertThat(result.expiresAt()).isEqualTo(T0.plusSeconds(7200));
    }

    @Test
    @DisplayName("Đề nghị huỷ đã xử lý: giao dịch huỷ là đã đồng ý, sang sự cố giao nhận là bị từ chối")
    void toCancelRequest_whenHandled_showsAcceptedOrRejected() {
        CancelRequestInfo info =
                new CancelRequestInfo(20L, T0, "COUNTERPART_NOT_SHIPPED", "Bên kia chưa gửi hàng", null);

        assertThat(AdminRequestDetailService.toCancelRequest(
                                swapShippingCore("CANCELLED", info, RescheduleInfo.EMPTY, null))
                        .state())
                .isEqualTo("ACCEPTED");
        AdminCancelRequest rejected = AdminRequestDetailService.toCancelRequest(
                swapShippingCore("DISPUTED", info, RescheduleInfo.EMPTY, null));
        assertThat(rejected.state()).isEqualTo("REJECTED");
        assertThat(rejected.requestedByRole()).isEqualTo("Người nhận");
        assertThat(rejected.expiresAt()).isNull();
    }

    @Test
    @DisplayName("Không có đề nghị huỷ thì không có khối")
    void toCancelRequest_whenNone_returnsNull() {
        assertThat(AdminRequestDetailService.toCancelRequest(
                        swapShippingCore("LOGISTICS_CONFIRMED", CancelRequestInfo.EMPTY, RescheduleInfo.EMPTY, null)))
                .isNull();
    }

    @Test
    @DisplayName("Dời lịch: hiện số lần đã dời và đề nghị đang chờ")
    void toReschedule_whenPending_showsCountAndProposal() {
        RescheduleInfo info = new RescheduleInfo(20L, T0.plusSeconds(500), T0.plusSeconds(90000), "Thư viện", 1);

        AdminReschedule result = AdminRequestDetailService.toReschedule(
                swapShippingCore("LOGISTICS_CONFIRMED", CancelRequestInfo.EMPTY, info, null));

        assertThat(result.count()).isEqualTo(1);
        assertThat(result.pending()).isTrue();
        assertThat(result.proposedByRole()).isEqualTo("Người nhận");
        assertThat(result.expiresAt()).isEqualTo(T0.plusSeconds(8000));
        assertThat(AdminRequestDetailService.toReschedule(swapShippingCore(
                        "LOGISTICS_CONFIRMED",
                        CancelRequestInfo.EMPTY,
                        new RescheduleInfo(null, null, null, null, 0),
                        null)))
                .isNull();
    }

    @Test
    @DisplayName("Giao hàng hai chiều: mỗi chiều có địa chỉ, ngày gửi, ngày nhận và bước")
    void toShippingLegs_whenSwapShipping_returnsTwoLegsWithSteps() {
        AdminRequestCore core = swapShippingCore(
                "AWAITING_HANDOVER", CancelRequestInfo.EMPTY, RescheduleInfo.EMPTY, T0.plusSeconds(1000));

        List<AdminShippingLeg> legs = AdminRequestDetailService.toShippingLegs(
                core,
                new AdminRequestItemInfo(10L, "Máy ảnh", null, null, null),
                new AdminRequestItemInfo(30L, "Mũ bảo hiểm", null, null, null));

        assertThat(legs).hasSize(2);
        assertThat(legs.get(0).itemTitle()).isEqualTo("Máy ảnh");
        assertThat(legs.get(0).senderRole()).isEqualTo("Chủ bài");
        assertThat(legs.get(0).address()).isEqualTo("12 Lê Lợi");
        assertThat(legs.get(0).shippedAt()).isNotNull();
        // Người nhận đã nhận chiều 1 (receiverHandoverAt) nên bước là Đã nhận, chiều 2 chưa gửi
        assertThat(legs.get(0).step()).isEqualTo("Đã nhận");
        assertThat(legs.get(1).address()).isEqualTo("34 Trần Phú");
        assertThat(legs.get(1).step()).isEqualTo("Chờ gửi");
    }

    @Test
    @DisplayName("Gặp trực tiếp không có chiều giao hàng")
    void toShippingLegs_whenInPerson_returnsEmpty() {
        assertThat(AdminRequestDetailService.toShippingLegs(core("COMPLETED", false, null), null, null))
                .isEmpty();
    }

    @Test
    @DisplayName("Dòng thời gian thêm đề nghị huỷ, dời lịch, gửi hàng và nhận hàng")
    void buildTimeline_whenShippingWithRequests_addsNewEvents() {
        AdminRequestCore core = swapShippingCore(
                "AWAITING_HANDOVER",
                new CancelRequestInfo(99L, T0.plusSeconds(1800), "BUSY", "Tôi bận đột xuất", null),
                new RescheduleInfo(20L, T0.plusSeconds(500), T0.plusSeconds(90000), null, 0),
                T0.plusSeconds(1000));

        List<AdminTimelineEvent> events = AdminRequestDetailService.buildTimeline(core);

        assertThat(events)
                .extracting(AdminTimelineEvent::label)
                .contains(
                        "Chủ bài gửi hàng (chiều 1)",
                        "Người nhận nhận được hàng (chiều 1)",
                        "Chủ bài đề nghị huỷ giao dịch",
                        "Người nhận đề nghị dời lịch");
        assertThat(events).extracting(AdminTimelineEvent::at).isSorted();
    }
}
