package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.request.dto.HandoverConfirmRequest;
import com.shareloop.request.dto.RequestDeadlines;
import com.shareloop.request.entity.CancelRequestCode;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** Mốc hạn do backend tính (GN-13) và các việc nhắc hạn không gửi lặp. */
@ExtendWith(MockitoExtension.class)
class RequestDeadlineTest {

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
    ApplicationEventPublisher eventPublisher;

    RequestService requestService;
    RequestDeadlineCalculator calculator;

    @BeforeEach
    void setUp() {
        requestService = new RequestService(
                requestRepository,
                notificationService,
                configService,
                itemService,
                itemQueryService,
                userService,
                null,
                null,
                null,
                null,
                eventPublisher,
                null);
        calculator = new RequestDeadlineCalculator(configService);
        lenient().when(itemQueryService.getSummary(ITEM_ID)).thenReturn(new ItemSummary(ITEM_ID, DONOR_ID, null, null));
        lenient().when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(configService.getInt(ConfigKey.CHAT_OPEN_DAYS)).thenReturn(10);
        lenient()
                .when(configService.getInt(ConfigKey.CONFIRMED_STALE_CANCEL_DAYS))
                .thenReturn(3);
        lenient().when(configService.getInt(ConfigKey.AUTO_CONFIRM_DAYS)).thenReturn(3);
        lenient()
                .when(configService.getInt(ConfigKey.AUTO_CONFIRM_DAYS_SHIPPING))
                .thenReturn(7);
        lenient()
                .when(configService.getInt(ConfigKey.SHIPPING_SEND_DEADLINE_DAYS))
                .thenReturn(3);
        lenient()
                .when(configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS))
                .thenReturn(24);
        lenient()
                .when(configService.getInt(ConfigKey.DEADLINE_REMINDER_HOURS_BEFORE_RESPONSE))
                .thenReturn(6);
        lenient().when(configService.getInt(ConfigKey.COMPLAINT_WINDOW_HOURS)).thenReturn(24);
    }

    private Request request(RequestStatus status, String method, Instant meetingTime) {
        Request request = new Request(ITEM_ID, RECEIVER_ID, null, "GIVE");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", status);
        ReflectionTestUtils.setField(request, "deliveryMethod", method);
        ReflectionTestUtils.setField(request, "meetingTime", meetingTime);
        ReflectionTestUtils.setField(request, "meetingPlace", "Cổng trường");
        lenient().when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        lenient().when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
        return request;
    }

    // ---------- mốc hạn ----------

    @Test
    void autoCancelAt_whenNotYetConfirmed_isReservedAtPlusChatOpenDays() {
        // given
        Instant reservedAt = Instant.parse("2026-12-01T08:00:00Z");
        Request request = request(RequestStatus.AWAITING_LOGISTICS, null, null);
        ReflectionTestUtils.setField(request, "reservedAt", reservedAt);

        // when
        RequestDeadlines deadlines = calculator.of(request);

        // then
        assertThat(deadlines.autoCancelAt()).isEqualTo(reservedAt.plus(10, ChronoUnit.DAYS));
        assertThat(deadlines.autoConfirmAt()).isNull();
        assertThat(deadlines.sendDeadlineAt()).isNull();
    }

    @Test
    void autoCancelAt_whenConfirmedInPerson_isMeetingTimePlusStaleDays() {
        // given
        Instant meeting = Instant.parse("2026-12-05T08:00:00Z");
        Request request = request(RequestStatus.LOGISTICS_CONFIRMED, "IN_PERSON", meeting);

        // when / then
        assertThat(calculator.of(request).autoCancelAt()).isEqualTo(meeting.plus(3, ChronoUnit.DAYS));
    }

    @Test
    void autoCancelAt_whenConfirmedShipping_isSendDeadlinePlusStaleDaysAndSendDeadlineIsMeetingPlusDays() {
        // given
        Instant sendDate = Instant.parse("2026-12-05T08:00:00Z");
        Request request = request(RequestStatus.LOGISTICS_CONFIRMED, "SHIPPING", sendDate);

        // when
        RequestDeadlines deadlines = calculator.of(request);

        // then
        assertThat(deadlines.sendDeadlineAt()).isEqualTo(sendDate.plus(3, ChronoUnit.DAYS));
        assertThat(deadlines.autoCancelAt()).isEqualTo(sendDate.plus(6, ChronoUnit.DAYS));
    }

    @Test
    void autoCancelAt_whenCancelRequestPending_isNullAndCancelRequestExpiresAtIsSet() {
        // given
        Instant requestedAt = Instant.parse("2026-12-01T08:00:00Z");
        Request request = request(
                RequestStatus.LOGISTICS_CONFIRMED, "IN_PERSON", Instant.now().plus(1, ChronoUnit.DAYS));
        request.requestCancel(DONOR_ID, true, CancelRequestCode.BUSY, null, requestedAt);

        // when
        RequestDeadlines deadlines = calculator.of(request);

        // then
        assertThat(deadlines.autoCancelAt()).isNull();
        assertThat(deadlines.cancelRequestExpiresAt()).isEqualTo(requestedAt.plus(24, ChronoUnit.HOURS));
    }

    @Test
    void rescheduleExpiresAt_isTheEarlierOfOldMeetingTimeAndResponseDeadline() {
        // given: giờ hẹn cũ sau 2 giờ nên sớm hơn hạn phản hồi 24 giờ
        Instant now = Instant.now();
        Request request = request(RequestStatus.LOGISTICS_CONFIRMED, "IN_PERSON", now.plus(2, ChronoUnit.HOURS));
        request.proposeReschedule(DONOR_ID, now.plus(3, ChronoUnit.DAYS), null, now, 2);

        // when / then
        assertThat(calculator.of(request).rescheduleExpiresAt()).isEqualTo(request.getMeetingTime());
    }

    @Test
    void autoConfirmAt_whenOnePartyConfirmedInPerson_isConfirmTimePlusAutoConfirmDays() {
        // given
        Instant confirmedAt = Instant.parse("2026-12-01T08:00:00Z");
        Request request = request(RequestStatus.AWAITING_HANDOVER, "IN_PERSON", confirmedAt);
        ReflectionTestUtils.setField(request, "donorHandoverAt", confirmedAt);

        // when / then
        assertThat(calculator.of(request).autoConfirmAt()).isEqualTo(confirmedAt.plus(3, ChronoUnit.DAYS));
    }

    @Test
    void autoConfirmAt_whenShipping_isEarliestShippedLegPlusShippingDays() {
        // given: chiều 1 gửi sớm hơn chiều 2
        Instant first = Instant.parse("2026-12-01T08:00:00Z");
        Instant second = Instant.parse("2026-12-02T08:00:00Z");
        Request request = request(RequestStatus.AWAITING_HANDOVER, "SHIPPING", first);
        ReflectionTestUtils.setField(request, "offeredItemId", 2L);
        ReflectionTestUtils.setField(request, "donorShippedAt", first);
        ReflectionTestUtils.setField(request, "receiverShippedAt", second);

        // when
        RequestDeadlines deadlines = calculator.of(request);

        // then
        assertThat(deadlines.leg1AutoReceiveAt()).isEqualTo(first.plus(7, ChronoUnit.DAYS));
        assertThat(deadlines.leg2AutoReceiveAt()).isEqualTo(second.plus(7, ChronoUnit.DAYS));
        assertThat(deadlines.autoConfirmAt()).isEqualTo(first.plus(7, ChronoUnit.DAYS));
    }

    // ---------- nhắc hạn ----------

    @Test
    void remindCancelRequest_whenWithinSixHoursOfDeadline_remindsResponderOnlyOnce() {
        // given: đề nghị cách đây 19 giờ, hạn 24 giờ nên còn 5 giờ
        Instant now = Instant.now();
        Request request = request(RequestStatus.LOGISTICS_CONFIRMED, "IN_PERSON", now.plus(1, ChronoUnit.DAYS));
        request.requestCancel(DONOR_ID, true, CancelRequestCode.BUSY, null, now.minus(19, ChronoUnit.HOURS));
        when(notificationService.hasNotificationSince(anyLong(), eq("CANCEL_REQUEST_REMINDER"), anyString(), any()))
                .thenReturn(false, true);

        // when
        boolean first = requestService.remindCancelRequest(REQUEST_ID, now);
        boolean second = requestService.remindCancelRequest(REQUEST_ID, now);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        verify(notificationService, times(1))
                .notify(eq(RECEIVER_ID), eq("CANCEL_REQUEST_REMINDER"), anyString(), anyString(), anyString());
    }

    @Test
    void remindCancelRequest_whenStillMoreThanSixHoursLeft_doesNotRemind() {
        // given: đề nghị mới 2 giờ trước, còn 22 giờ
        Instant now = Instant.now();
        Request request = request(RequestStatus.LOGISTICS_CONFIRMED, "IN_PERSON", now.plus(1, ChronoUnit.DAYS));
        request.requestCancel(DONOR_ID, true, CancelRequestCode.BUSY, null, now.minus(2, ChronoUnit.HOURS));

        // when / then
        assertThat(requestService.remindCancelRequest(REQUEST_ID, now)).isFalse();
        verify(notificationService, never()).notify(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void remindReschedule_whenWithinSixHoursOfDeadline_remindsResponder() {
        // given: đề nghị dời cách đây 20 giờ, giờ hẹn cũ còn xa
        Instant now = Instant.now();
        Request request = request(RequestStatus.LOGISTICS_CONFIRMED, "IN_PERSON", now.plus(3, ChronoUnit.DAYS));
        request.proposeReschedule(DONOR_ID, now.plus(4, ChronoUnit.DAYS), null, now.minus(20, ChronoUnit.HOURS), 2);
        // proposeReschedule cần trước giờ hẹn: giờ hẹn là 3 ngày sau nên hợp lệ

        // when
        boolean sent = requestService.remindReschedule(REQUEST_ID, now);

        // then
        assertThat(sent).isTrue();
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("RESCHEDULE_REMINDER"), anyString(), anyString(), anyString());
    }

    @Test
    void remindMeeting_whenWithin24HoursOfMeeting_remindsBothPartiesOncePerType() {
        // given
        Instant now = Instant.now();
        request(RequestStatus.LOGISTICS_CONFIRMED, "IN_PERSON", now.plus(20, ChronoUnit.HOURS));
        when(notificationService.hasNotificationSince(anyLong(), eq("MEETING_REMINDER"), anyString(), any()))
                .thenReturn(false, false, true, true);

        // when
        boolean first = requestService.remindMeeting(REQUEST_ID, now);
        boolean second = requestService.remindMeeting(REQUEST_ID, now);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        verify(notificationService, times(2))
                .notify(anyLong(), eq("MEETING_REMINDER"), anyString(), anyString(), anyString());
    }

    @Test
    void remindScheduleAutoCancel_whenWithin24HoursOfAutoCancel_remindsBothParties() {
        // given: chọn người cách đây 9 ngày 6 giờ, tự huỷ sau 10 ngày nên còn 18 giờ
        Instant now = Instant.now();
        Request request = request(RequestStatus.AWAITING_LOGISTICS, null, null);
        ReflectionTestUtils.setField(
                request, "reservedAt", now.minus(9, ChronoUnit.DAYS).minus(6, ChronoUnit.HOURS));

        // when
        boolean sent = requestService.remindScheduleAutoCancel(REQUEST_ID, now);

        // then
        assertThat(sent).isTrue();
        verify(notificationService, times(2))
                .notify(anyLong(), eq("SCHEDULE_AUTO_CANCEL_REMINDER"), anyString(), anyString(), anyString());
    }

    @Test
    void remindAutoConfirm_whenWithin24HoursOfAutoConfirm_remindsSilentPartyWithTime() {
        // given: chủ bài bấm hoàn tất cách đây 2 ngày 6 giờ, tự hoàn tất sau 3 ngày nên còn 18 giờ
        Instant now = Instant.now();
        Request request = request(RequestStatus.AWAITING_HANDOVER, "IN_PERSON", now.minus(3, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(
                request, "donorHandoverAt", now.minus(2, ChronoUnit.DAYS).minus(6, ChronoUnit.HOURS));

        // when
        boolean sent = requestService.remindAutoConfirm(REQUEST_ID, now);

        // then
        assertThat(sent).isTrue();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService)
                .notify(
                        eq(RECEIVER_ID),
                        eq("HANDOVER_AUTO_CONFIRM_REMINDER"),
                        anyString(),
                        body.capture(),
                        anyString());
        assertThat(body.getValue()).contains("tự ghi nhận hoàn tất lúc");
    }

    @Test
    void confirmHandover_whenFirstPartyConfirms_tellsOtherPartyTheAutoCompleteTime() {
        // given
        Request request = request(
                RequestStatus.LOGISTICS_CONFIRMED, "IN_PERSON", Instant.now().minus(1, ChronoUnit.HOURS));

        // when
        requestService.confirmHandover(REQUEST_ID, DONOR_ID, new HandoverConfirmRequest(null));

        // then
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("HANDOVER_CONFIRMED"), anyString(), body.capture(), anyString());
        assertThat(body.getValue()).contains("hệ thống sẽ tự ghi nhận hoàn tất lúc");
        assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_HANDOVER);
    }
}
