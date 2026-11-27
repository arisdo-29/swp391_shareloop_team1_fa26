package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.CancelRequestRequest;
import com.shareloop.request.dto.HandoverConfirmRequest;
import com.shareloop.request.dto.HandoverFailedRequest;
import com.shareloop.request.dto.RefuseHandoverRequest;
import com.shareloop.request.dto.RejectCancelRequest;
import com.shareloop.request.entity.CancelRequestCode;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.event.CancelRequestRejectedEvent;
import com.shareloop.request.event.SilentCancelRequestReportedEvent;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.service.UserService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** Đề nghị huỷ có xác nhận của bên kia (GN-06): tạo, rút, đồng ý, không đồng ý và im lặng quá hạn. */
@ExtendWith(MockitoExtension.class)
class RequestCancelRequestTest {

    private static final long REQUEST_ID = 101L;
    private static final long ITEM_ID = 1L;
    private static final long DONOR_ID = 20L;
    private static final long RECEIVER_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-12-01T10:00:00Z");

    @Mock
    RequestRepository requestRepository;

    @Mock
    NotificationService notificationService;

    @Mock
    ConfigService configService;

    @Mock
    ItemService itemService;

    @Mock
    ItemQueryService itemQueryService;

    @Mock
    UserService userService;

    @Mock
    ReputationService reputationService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    RequestService requestService;

    @BeforeEach
    void setUp() {
        requestService = new RequestService(
                requestRepository,
                notificationService,
                configService,
                itemService,
                itemQueryService,
                userService,
                reputationService,
                null,
                null,
                null,
                eventPublisher,
                null);
        org.mockito.Mockito.lenient()
                .when(itemQueryService.getSummary(ITEM_ID))
                .thenReturn(new ItemSummary(ITEM_ID, DONOR_ID, null, null));
        org.mockito.Mockito.lenient()
                .when(requestRepository.save(any(Request.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** Giao dịch đã chốt lịch, giờ hẹn cách bây giờ meetingOffsetHours giờ (âm là đã qua giờ hẹn). */
    private Request confirmedRequest(long meetingOffsetHours) {
        Request request = new Request(ITEM_ID, RECEIVER_ID, null, "GIVE");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", RequestStatus.LOGISTICS_CONFIRMED);
        ReflectionTestUtils.setField(request, "deliveryMethod", "IN_PERSON");
        ReflectionTestUtils.setField(request, "meetingTime", Instant.now().plus(meetingOffsetHours, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(request, "contactRevealedAt", Instant.now().minus(1, ChronoUnit.HOURS));
        org.mockito.Mockito.lenient()
                .when(requestRepository.findByIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.of(request));
        org.mockito.Mockito.lenient()
                .when(requestRepository.findById(REQUEST_ID))
                .thenReturn(Optional.of(request));
        return request;
    }

    private Request withPendingCancelRequest(long requesterId, CancelRequestCode code, long meetingOffsetHours) {
        Request request = confirmedRequest(meetingOffsetHours);
        request.requestCancel(requesterId, requesterId == DONOR_ID, code, "ghi chú thêm", NOW);
        return request;
    }

    @Test
    void handoverFailed_whenBeforeMeetingTime_throwsBeforeMeetingTime() {
        // given
        confirmedRequest(5);

        // when / then
        assertThatThrownBy(() -> requestService.handoverFailed(
                        REQUEST_ID, RECEIVER_ID, new HandoverFailedRequest("Không đến điểm hẹn", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_BEFORE_MEETING_TIME));
    }

    @Test
    void refuseHandover_whenBeforeMeetingTime_throwsBeforeMeetingTime() {
        // given
        confirmedRequest(5);

        // when / then
        assertThatThrownBy(() -> requestService.refuseHandover(
                        REQUEST_ID, RECEIVER_ID, new RefuseHandoverRequest("Món không đúng mô tả", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_BEFORE_MEETING_TIME));
    }

    @Test
    void handoverFailed_whenAfterMeetingAndOtherNotConfirmed_createsCancelRequestWithoutCancelling() {
        // given
        Request request = confirmedRequest(-2);
        when(configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS)).thenReturn(24);

        // when
        requestService.handoverFailed(
                REQUEST_ID, RECEIVER_ID, new HandoverFailedRequest(CancelRequestCode.COUNTERPART_NO_SHOW, null, null));

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
        assertThat(request.getCancelRequestedBy()).isEqualTo(RECEIVER_ID);
        assertThat(request.getCancelRequestCode()).isEqualTo(CancelRequestCode.COUNTERPART_NO_SHOW);
        verify(notificationService).notify(eq(DONOR_ID), eq("CANCEL_REQUESTED"), anyString(), anyString(), anyString());
        verify(itemService, never()).releaseAfterCancel(eq(ITEM_ID), any());
    }

    @Test
    void handoverFailed_whenCodeNotAllowedAfterMeeting_throwsReasonInvalid() {
        // given: "Tôi bận đột xuất" chỉ dùng trước giờ hẹn
        confirmedRequest(-2);

        // when / then
        assertThatThrownBy(() -> requestService.handoverFailed(
                        REQUEST_ID, RECEIVER_ID, new HandoverFailedRequest(CancelRequestCode.BUSY, null, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_CANCEL_REASON_INVALID));
    }

    @Test
    void requestCancel_whenBeforeMeetingTime_savesPendingRequestAndNotifiesOtherParty() {
        // given
        Request request = confirmedRequest(5);
        when(configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS)).thenReturn(24);

        // when
        requestService.requestCancel(REQUEST_ID, DONOR_ID, new CancelRequestRequest(CancelRequestCode.BUSY, null));

        // then
        assertThat(request.hasPendingCancelRequest()).isTrue();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("CANCEL_REQUESTED"), anyString(), body.capture(), anyString());
        assertThat(body.getValue()).contains("họ bận đột xuất").contains("Bạn có đồng ý không?");
    }

    @Test
    void requestCancel_whenAfterMeetingTime_throwsInvalidState() {
        // given
        confirmedRequest(-1);

        // when / then
        assertThatThrownBy(() -> requestService.requestCancel(
                        REQUEST_ID, DONOR_ID, new CancelRequestRequest(CancelRequestCode.BUSY, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION));
    }

    @Test
    void requestCancel_whenOtherReasonWithoutText_throwsReasonInvalid() {
        // given
        confirmedRequest(5);

        // when / then
        assertThatThrownBy(() -> requestService.requestCancel(
                        REQUEST_ID, DONOR_ID, new CancelRequestRequest(CancelRequestCode.OTHER, "ab")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_CANCEL_REASON_INVALID));
    }

    @Test
    void requestCancel_whenAlreadyPending_throwsPending() {
        // given
        withPendingCancelRequest(DONOR_ID, CancelRequestCode.BUSY, 5);

        // when / then
        assertThatThrownBy(() -> requestService.requestCancel(
                        REQUEST_ID, RECEIVER_ID, new CancelRequestRequest(CancelRequestCode.BUSY, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_CANCEL_REQUEST_PENDING));
    }

    @Test
    void acceptCancelRequest_whenOtherPartyAgrees_cancelsReleasesItemAndPenalizesNobody() {
        // given
        Request request = withPendingCancelRequest(DONOR_ID, CancelRequestCode.NO_LONGER_NEEDED, 5);

        // when
        requestService.acceptCancelRequest(REQUEST_ID, RECEIVER_ID);

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(request.getCancelReason()).startsWith(Request.CANCEL_BY_DONOR);
        verify(itemService).releaseAfterCancel(eq(ITEM_ID), any());
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
        verifyNoInteractions(reputationService);
    }

    @Test
    void acceptCancelRequest_whenCallerIsRequester_throwsForbidden() {
        // given
        withPendingCancelRequest(DONOR_ID, CancelRequestCode.BUSY, 5);

        // when / then
        assertThatThrownBy(() -> requestService.acceptCancelRequest(REQUEST_ID, DONOR_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_FORBIDDEN));
    }

    @Test
    void rejectCancelRequest_whenOtherPartyDisagrees_movesToDisputedAndPublishesEvent() {
        // given
        Request request = withPendingCancelRequest(RECEIVER_ID, CancelRequestCode.COUNTERPART_NO_SHOW, -2);

        // when
        requestService.rejectCancelRequest(
                REQUEST_ID, DONOR_ID, new RejectCancelRequest("Tôi đã đứng đợi ở đúng điểm hẹn", null));

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.DISPUTED);
        ArgumentCaptor<CancelRequestRejectedEvent> captor = ArgumentCaptor.forClass(CancelRequestRejectedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().reporterId()).isEqualTo(RECEIVER_ID);
        assertThat(captor.getValue().note())
                .contains("Bên kia không đến điểm hẹn")
                .contains("Tôi đã đứng đợi ở đúng điểm hẹn");
        verify(itemService, never()).releaseAfterCancel(eq(ITEM_ID), any());
    }

    @Test
    void confirmHandover_whenOtherPartyHasPendingCancelRequest_movesToDisputed() {
        // given
        Request request = withPendingCancelRequest(DONOR_ID, CancelRequestCode.NO_LONGER_NEEDED, -1);

        // when
        requestService.confirmHandover(REQUEST_ID, RECEIVER_ID, new HandoverConfirmRequest(null));

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.DISPUTED);
        verify(eventPublisher).publishEvent(any(CancelRequestRejectedEvent.class));
    }

    @Test
    void confirmHandover_whenCallerIsRequesterOfPendingCancel_throwsPending() {
        // given
        withPendingCancelRequest(DONOR_ID, CancelRequestCode.NO_LONGER_NEEDED, 5);

        // when / then
        assertThatThrownBy(() -> requestService.confirmHandover(REQUEST_ID, DONOR_ID, new HandoverConfirmRequest(null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_CANCEL_REQUEST_PENDING));
    }

    @Test
    void withdrawCancelRequest_whenRequesterWithdraws_clearsRequestAndNotifiesOtherParty() {
        // given
        Request request = withPendingCancelRequest(DONOR_ID, CancelRequestCode.BUSY, 5);

        // when
        requestService.withdrawCancelRequest(REQUEST_ID, DONOR_ID);

        // then
        assertThat(request.hasPendingCancelRequest()).isFalse();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("CANCEL_REQUEST_WITHDRAWN"), anyString(), anyString(), anyString());
    }

    @Test
    void withdrawCancelRequest_whenCallerIsNotRequester_throwsForbidden() {
        // given
        withPendingCancelRequest(DONOR_ID, CancelRequestCode.BUSY, 5);

        // when / then
        assertThatThrownBy(() -> requestService.withdrawCancelRequest(REQUEST_ID, RECEIVER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_FORBIDDEN));
    }

    @Test
    void expireCancelRequest_whenSilentPastDeadline_cancelsWithoutReportForOrdinaryReason() {
        // given
        Request request = withPendingCancelRequest(DONOR_ID, CancelRequestCode.BUSY, 5);
        when(configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS)).thenReturn(24);

        // when
        boolean expired = requestService.expireCancelRequest(REQUEST_ID, NOW.plus(25, ChronoUnit.HOURS));

        // then
        assertThat(expired).isTrue();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(request.getCancelReason()).startsWith(Request.CANCEL_BY_SYSTEM);
        verify(itemService).releaseAfterCancel(eq(ITEM_ID), any());
        verify(eventPublisher, never()).publishEvent(any(SilentCancelRequestReportedEvent.class));
        verifyNoInteractions(reputationService);
    }

    @Test
    void expireCancelRequest_whenNoShowReasonAndSilent_cancelsAndPublishesNoShowReportEvent() {
        // given
        withPendingCancelRequest(RECEIVER_ID, CancelRequestCode.COUNTERPART_NO_SHOW, -2);
        when(configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS)).thenReturn(24);

        // when
        requestService.expireCancelRequest(REQUEST_ID, NOW.plus(25, ChronoUnit.HOURS));

        // then
        ArgumentCaptor<SilentCancelRequestReportedEvent> captor =
                ArgumentCaptor.forClass(SilentCancelRequestReportedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().reporterId()).isEqualTo(RECEIVER_ID);
        assertThat(captor.getValue().reportedUserId()).isEqualTo(DONOR_ID);
        verifyNoInteractions(reputationService);
    }

    @Test
    void expireCancelRequest_whenBeforeDeadline_doesNothing() {
        // given
        Request request = withPendingCancelRequest(DONOR_ID, CancelRequestCode.BUSY, 5);
        when(configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS)).thenReturn(24);

        // when
        boolean expired = requestService.expireCancelRequest(REQUEST_ID, NOW.plus(23, ChronoUnit.HOURS));

        // then
        assertThat(expired).isFalse();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
    }
}
