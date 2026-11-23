package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import com.shareloop.request.dto.RescheduleRequest;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** Giới hạn giờ hẹn, dời lịch và tự huỷ giao dịch đã chốt lịch mà không ai làm gì (GN-11, GN-12). */
@ExtendWith(MockitoExtension.class)
class RequestRescheduleTest {

    private static final long REQUEST_ID = 101L;
    private static final long ITEM_ID = 1L;
    private static final long DONOR_ID = 20L;
    private static final long RECEIVER_ID = 10L;

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
        lenient().when(itemQueryService.getSummary(ITEM_ID)).thenReturn(new ItemSummary(ITEM_ID, DONOR_ID, null, null));
        lenient().when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(configService.getInt(ConfigKey.MEETING_MAX_DAYS_AHEAD)).thenReturn(7);
        lenient().when(configService.getInt(ConfigKey.RESCHEDULE_MAX_COUNT)).thenReturn(2);
        lenient()
                .when(configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS))
                .thenReturn(24);
        lenient()
                .when(configService.getInt(ConfigKey.CONFIRMED_STALE_CANCEL_DAYS))
                .thenReturn(3);
    }

    private Request confirmedRequest(Instant meetingTime) {
        Request request = new Request(ITEM_ID, RECEIVER_ID, null, "GIVE");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", RequestStatus.LOGISTICS_CONFIRMED);
        ReflectionTestUtils.setField(request, "deliveryMethod", "IN_PERSON");
        ReflectionTestUtils.setField(request, "meetingPlace", "Cổng trường");
        ReflectionTestUtils.setField(request, "meetingTime", meetingTime);
        ReflectionTestUtils.setField(request, "contactRevealedAt", Instant.now().minus(1, ChronoUnit.HOURS));
        lenient().when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
        lenient().when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        return request;
    }

    private static Instant inDays(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    private static Instant daysAgo(long days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    @Test
    void updateSchedule_whenMeetingTimeIsEightDaysAhead_throwsScheduleTooFar() {
        // given
        Request request = new Request(ITEM_ID, RECEIVER_ID, null, "GIVE");
        request.select(Instant.now());
        when(requestRepository.findByIdForScheduleUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
        ScheduleRequest dto = new ScheduleRequest("IN_PERSON", "Cổng trường", inDays(8), true);

        // when / then
        assertThatThrownBy(() -> requestService.updateSchedule(REQUEST_ID, DONOR_ID, dto))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_SCHEDULE_TOO_FAR);
                    assertThat(ex.getMessage()).isEqualTo("Chỉ hẹn trong vòng 7 ngày tới");
                });
    }

    @Test
    void proposeReschedule_whenMeetingTimeIsEightDaysAhead_throwsScheduleTooFar() {
        // given
        confirmedRequest(inDays(1));

        // when / then
        assertThatThrownBy(() ->
                        requestService.proposeReschedule(REQUEST_ID, DONOR_ID, new RescheduleRequest(inDays(8), null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_SCHEDULE_TOO_FAR));
    }

    @Test
    void acceptReschedule_whenOtherPartyAgrees_changesMeetingTimeAndRaisesCounter() {
        // given
        Request request = confirmedRequest(inDays(1));
        Instant newTime = inDays(3);
        requestService.proposeReschedule(REQUEST_ID, DONOR_ID, new RescheduleRequest(newTime, "Thư viện"));
        assertThat(request.hasPendingReschedule()).isTrue();

        // when
        requestService.acceptReschedule(REQUEST_ID, RECEIVER_ID);

        // then
        assertThat(request.getMeetingTime()).isEqualTo(newTime);
        assertThat(request.getMeetingPlace()).isEqualTo("Thư viện");
        assertThat(request.getRescheduleCount()).isEqualTo(1);
        assertThat(request.hasPendingReschedule()).isFalse();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("RESCHEDULE_ACCEPTED"), anyString(), anyString(), anyString());
    }

    @Test
    void proposeReschedule_whenThirdTime_throwsLimitReached() {
        // given: đã dời hai lần được đồng ý
        Request request = confirmedRequest(inDays(1));
        ReflectionTestUtils.setField(request, "rescheduleCount", 2);

        // when / then
        assertThatThrownBy(() ->
                        requestService.proposeReschedule(REQUEST_ID, DONOR_ID, new RescheduleRequest(inDays(2), null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(RequestErrorCode.REQUEST_RESCHEDULE_LIMIT_REACHED));
    }

    @Test
    void rejectReschedule_whenOtherPartyRefuses_keepsOldScheduleAndCounter() {
        // given
        Instant oldTime = inDays(1);
        Request request = confirmedRequest(oldTime);
        requestService.proposeReschedule(REQUEST_ID, DONOR_ID, new RescheduleRequest(inDays(3), null));

        // when
        requestService.rejectReschedule(REQUEST_ID, RECEIVER_ID);

        // then
        assertThat(request.getMeetingTime()).isEqualTo(oldTime);
        assertThat(request.getRescheduleCount()).isZero();
        assertThat(request.hasPendingReschedule()).isFalse();
    }

    @Test
    void proposeReschedule_whenSomeoneAlreadyConfirmedHandover_throwsNotAllowed() {
        // given
        Request request = confirmedRequest(inDays(1));
        ReflectionTestUtils.setField(request, "donorHandoverAt", Instant.now());

        // when / then
        assertThatThrownBy(() -> requestService.proposeReschedule(
                        REQUEST_ID, RECEIVER_ID, new RescheduleRequest(inDays(2), null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_RESCHEDULE_NOT_ALLOWED));
    }

    @Test
    void proposeReschedule_whenCancelRequestPending_throwsCancelRequestPending() {
        // given
        Request request = confirmedRequest(inDays(1));
        ReflectionTestUtils.setField(request, "cancelRequestedAt", Instant.now());
        ReflectionTestUtils.setField(request, "cancelRequestedBy", DONOR_ID);

        // when / then
        assertThatThrownBy(() -> requestService.proposeReschedule(
                        REQUEST_ID, RECEIVER_ID, new RescheduleRequest(inDays(2), null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_CANCEL_REQUEST_PENDING));
    }

    @Test
    void expireReschedule_whenResponseHoursPassed_keepsOldSchedule() {
        // given
        Instant oldTime = inDays(5);
        Request request = confirmedRequest(oldTime);
        requestService.proposeReschedule(REQUEST_ID, DONOR_ID, new RescheduleRequest(inDays(6), null));

        // when
        boolean expired =
                requestService.expireReschedule(REQUEST_ID, Instant.now().plus(25, ChronoUnit.HOURS));

        // then
        assertThat(expired).isTrue();
        assertThat(request.getMeetingTime()).isEqualTo(oldTime);
        assertThat(request.hasPendingReschedule()).isFalse();
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("RESCHEDULE_EXPIRED"), anyString(), anyString(), anyString());
    }

    @Test
    void expireReschedule_whenOldMeetingTimeComesBeforeResponseDeadline_expiresAtMeetingTime() {
        // given: giờ hẹn cũ sau 2 giờ, hạn phản hồi 24 giờ nên hạn là giờ hẹn cũ
        confirmedRequest(Instant.now().plus(2, ChronoUnit.HOURS));
        requestService.proposeReschedule(REQUEST_ID, DONOR_ID, new RescheduleRequest(inDays(3), null));

        // when
        boolean expired =
                requestService.expireReschedule(REQUEST_ID, Instant.now().plus(3, ChronoUnit.HOURS));

        // then
        assertThat(expired).isTrue();
    }

    @Test
    void expireReschedule_whenBeforeDeadline_doesNothing() {
        // given
        Request request = confirmedRequest(inDays(5));
        requestService.proposeReschedule(REQUEST_ID, DONOR_ID, new RescheduleRequest(inDays(6), null));

        // when
        boolean expired =
                requestService.expireReschedule(REQUEST_ID, Instant.now().plus(2, ChronoUnit.HOURS));

        // then
        assertThat(expired).isFalse();
        assertThat(request.hasPendingReschedule()).isTrue();
    }

    @Test
    void cancelStaleConfirmed_whenThreeDaysPastMeetingAndNobodyActed_cancelsReleasesItemAndPenalizesNobody() {
        // given
        Request request = confirmedRequest(daysAgo(4));

        // when
        boolean cancelled = requestService.cancelStaleConfirmed(REQUEST_ID, Instant.now());

        // then
        assertThat(cancelled).isTrue();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(request.getCancelReason()).startsWith(Request.CANCEL_BY_SYSTEM);
        verify(itemService).releaseAfterCancel(eq(ITEM_ID), any());
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
        verifyNoInteractions(reputationService);
    }

    @Test
    void cancelStaleConfirmed_whenJustResumedAfterRejectedCancel_doesNotCancelUntilStaleDaysFromResume() {
        // given: giờ hẹn đã qua 10 ngày nhưng giao dịch vừa quay lại sau khi Admin bác đề nghị huỷ
        Request request = confirmedRequest(daysAgo(10));
        ReflectionTestUtils.setField(request, "resumedAt", Instant.now().minus(1, ChronoUnit.HOURS));

        // when
        boolean cancelled = requestService.cancelStaleConfirmed(REQUEST_ID, Instant.now());

        // then: mốc tính lại từ lúc quay lại nên chưa huỷ
        assertThat(cancelled).isFalse();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
        verify(itemService, never()).releaseAfterCancel(anyLong(), any());
    }

    @Test
    void cancelStaleConfirmed_whenThreeDaysPastResume_cancelsAsUsual() {
        // given
        Request request = confirmedRequest(daysAgo(10));
        ReflectionTestUtils.setField(request, "resumedAt", daysAgo(4));

        // when
        boolean cancelled = requestService.cancelStaleConfirmed(REQUEST_ID, Instant.now());

        // then
        assertThat(cancelled).isTrue();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
    }

    @Test
    void resumeAfterRejectedCancel_whenResumed_recordsResumeTimeAndClearsCancelRequest() {
        // given
        Request request = confirmedRequest(daysAgo(1));
        ReflectionTestUtils.setField(request, "status", RequestStatus.DISPUTED);
        ReflectionTestUtils.setField(request, "cancelRequestedBy", DONOR_ID);
        ReflectionTestUtils.setField(request, "cancelRequestedAt", Instant.now());
        ReflectionTestUtils.setField(request, "cancelRequestCode", com.shareloop.request.entity.CancelRequestCode.BUSY);
        Instant now = Instant.now();

        // when
        request.resumeAfterRejectedCancel(now);

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
        assertThat(request.getResumedAt()).isEqualTo(now);
        assertThat(request.getCancelRequestedBy()).isNull();
    }

    @Test
    void cancelStaleConfirmed_whenOnlyTwoDaysPastMeeting_doesNothing() {
        // given
        Request request = confirmedRequest(daysAgo(2));

        // when
        boolean cancelled = requestService.cancelStaleConfirmed(REQUEST_ID, Instant.now());

        // then
        assertThat(cancelled).isFalse();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
        verify(itemService, never()).releaseAfterCancel(anyLong(), any());
    }

    @Test
    void remindStaleConfirmed_whenRunTwice_sendsReminderOnlyOnce() {
        // given: lần chạy đầu chưa có nhắc, lần hai đã có thông báo cùng loại kể từ giờ hẹn
        confirmedRequest(Instant.now().minus(30, ChronoUnit.HOURS));
        when(notificationService.hasNotificationSince(anyLong(), eq("HANDOVER_STALE_REMINDER"), anyString(), any()))
                .thenReturn(false, false, true, true);

        // when
        boolean first = requestService.remindStaleConfirmed(REQUEST_ID, Instant.now());
        boolean second = requestService.remindStaleConfirmed(REQUEST_ID, Instant.now());

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        verify(notificationService, org.mockito.Mockito.times(2))
                .notify(anyLong(), eq("HANDOVER_STALE_REMINDER"), anyString(), anyString(), anyString());
    }
}
