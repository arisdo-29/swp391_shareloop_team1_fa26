package com.shareloop.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.admin.dto.AdminTimelineEvent;
import com.shareloop.request.dto.AdminRequestCore;
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
}
