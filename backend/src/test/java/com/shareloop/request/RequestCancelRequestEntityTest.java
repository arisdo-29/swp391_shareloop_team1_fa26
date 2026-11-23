package com.shareloop.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.request.entity.CancelRequestCode;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Luật đề nghị huỷ trong entity Request (GN-06), nhất là cạnh LOGISTICS_CONFIRMED sang DISPUTED chỉ có một đường. */
class RequestCancelRequestEntityTest {

    private static final long DONOR_ID = 20L;
    private static final long RECEIVER_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-12-01T10:00:00Z");

    private Request confirmedRequest() {
        Request request = new Request(1L, RECEIVER_ID, null, "GIVE");
        ReflectionTestUtils.setField(request, "status", RequestStatus.LOGISTICS_CONFIRMED);
        ReflectionTestUtils.setField(request, "meetingTime", NOW.plus(1, ChronoUnit.DAYS));
        return request;
    }

    @Test
    void rejectCancelRequest_whenPending_movesLogisticsConfirmedToDisputed() {
        // given
        Request request = confirmedRequest();
        request.requestCancel(DONOR_ID, true, CancelRequestCode.BUSY, null, NOW);

        // when
        request.rejectCancelRequest(RECEIVER_ID);

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.DISPUTED);
    }

    @Test
    void openHandoverIssue_whenLogisticsConfirmed_stillThrows() {
        // given: không đường nào khác ngoài "không đồng ý đề nghị huỷ" được đi LOGISTICS_CONFIRMED sang DISPUTED
        Request request = confirmedRequest();

        // when / then
        assertThatThrownBy(() -> request.openHandoverIssue(false))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION));
    }

    @Test
    void rejectCancelRequest_whenNoPendingRequest_throwsNotFound() {
        // given
        Request request = confirmedRequest();

        // when / then
        assertThatThrownBy(() -> request.rejectCancelRequest(RECEIVER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(RequestErrorCode.REQUEST_CANCEL_REQUEST_NOT_FOUND));
        assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
    }

    @Test
    void requestCancel_whenOtherPartyAlreadyConfirmedHandover_throwsInvalidState() {
        // given: bên kia đã bấm hoàn tất thì phải đi đường khiếu nại, không tạo đề nghị
        Request request = confirmedRequest();
        request.confirmHandover(false, NOW);

        // when / then
        assertThatThrownBy(() -> request.requestCancel(DONOR_ID, true, CancelRequestCode.OTHER, "Lý do khác", NOW))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION));
    }

    @Test
    void cancel_whenCancelledForOtherReason_clearsPendingCancelRequest() {
        // given
        Request request = confirmedRequest();
        request.requestCancel(DONOR_ID, true, CancelRequestCode.BUSY, null, NOW);

        // when
        request.cancel(Request.CANCEL_BY_ADMIN + "Admin huỷ");

        // then: huỷ vì lý do khác thì không bị đếm là huỷ theo đề nghị
        assertThat(request.getCancelRequestedBy()).isNull();
    }

    @Test
    void acceptCancelRequest_whenAccepted_keepsTraceOfTheRequestForAnomalyCount() {
        // given
        Request request = confirmedRequest();
        request.requestCancel(DONOR_ID, true, CancelRequestCode.BUSY, null, NOW);

        // when
        request.acceptCancelRequest(RECEIVER_ID, false);

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(request.getCancelRequestedBy()).isEqualTo(DONOR_ID);
    }

    @Test
    void isBeforeMeetingTime_whenMeetingTimeInFuture_returnsTrue() {
        // given
        Request request = confirmedRequest();

        // when / then
        assertThat(request.isBeforeMeetingTime(NOW)).isTrue();
        assertThat(request.isBeforeMeetingTime(NOW.plus(2, ChronoUnit.DAYS))).isFalse();
    }
}
