package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.media.service.MediaService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.ConfirmLogisticsRequest;
import com.shareloop.request.dto.HandoverConfirmRequest;
import com.shareloop.request.dto.HandoverFailedRequest;
import com.shareloop.request.dto.RefuseHandoverRequest;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.request.dto.ShipmentRequest;
import com.shareloop.request.entity.CancelRequestCode;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.event.HandoverIssueReportedEvent;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.service.UserService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** Giao hàng hai chiều (GN-12): địa chỉ, gửi và nhận từng chiều, báo chưa nhận, tự hoàn tất, kẹt một chiều. */
@ExtendWith(MockitoExtension.class)
class RequestShippingTest {

    private static final long REQUEST_ID = 101L;
    private static final long ITEM_ID = 1L;
    private static final long OFFERED_ITEM_ID = 2L;
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
    MediaService mediaService;

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
                mediaService,
                null,
                eventPublisher,
                null);
        lenient().when(itemQueryService.getSummary(ITEM_ID)).thenReturn(new ItemSummary(ITEM_ID, DONOR_ID, null, null));
        lenient().when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(configService.getInt(ConfigKey.MEETING_MAX_DAYS_AHEAD)).thenReturn(7);
        lenient().when(configService.getInt(ConfigKey.CHAT_OPEN_DAYS)).thenReturn(10);
        lenient()
                .when(configService.getInt(ConfigKey.SHIPPING_SEND_DEADLINE_DAYS))
                .thenReturn(3);
        lenient()
                .when(configService.getInt(ConfigKey.AUTO_CONFIRM_DAYS_SHIPPING))
                .thenReturn(7);
        lenient()
                .when(configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS))
                .thenReturn(24);
        lenient()
                .when(configService.getInt(ConfigKey.CONFIRMED_STALE_CANCEL_DAYS))
                .thenReturn(3);
        lenient().when(configService.getInt(ConfigKey.COMPLAINT_WINDOW_HOURS)).thenReturn(24);
        lenient()
                .when(configService.getInt(ConfigKey.EXCHANGE_SHIPPING_WINDOW_MINUTES))
                .thenReturn(30);
        lenient()
                .when(configService.getInt(ConfigKey.EXCHANGE_SHIPPING_LEAD_MINUTES))
                .thenReturn(60);
        lenient()
                .when(configService.getInt(ConfigKey.EXCHANGE_SHIPPING_MORNING_START))
                .thenReturn(480);
        lenient()
                .when(configService.getInt(ConfigKey.EXCHANGE_SHIPPING_MORNING_END))
                .thenReturn(720);
        lenient()
                .when(configService.getInt(ConfigKey.EXCHANGE_SHIPPING_AFTERNOON_START))
                .thenReturn(780);
        lenient()
                .when(configService.getInt(ConfigKey.EXCHANGE_SHIPPING_AFTERNOON_END))
                .thenReturn(1020);
        lenient()
                .when(configService.getInt(ConfigKey.EXCHANGE_SHIPPING_REOPEN_HOURS))
                .thenReturn(24);
    }

    /** Giao dịch Giao hàng đã chốt lịch, ngày gửi dự kiến cách bây giờ sendDateOffsetDays ngày (âm là đã qua). */
    private Request shippingRequest(Long offeredItemId, long sendDateOffsetDays) {
        Request request = new Request(ITEM_ID, RECEIVER_ID, offeredItemId, offeredItemId == null ? "GIVE" : "SWAP");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", RequestStatus.LOGISTICS_CONFIRMED);
        ReflectionTestUtils.setField(request, "deliveryMethod", "SHIPPING");
        ReflectionTestUtils.setField(request, "meetingTime", Instant.now().plus(sendDateOffsetDays, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(request, "receiverAddress", "12 Lê Lợi");
        if (offeredItemId != null) {
            ReflectionTestUtils.setField(request, "donorAddress", "34 Trần Phú");
        }
        ReflectionTestUtils.setField(request, "contactRevealedAt", Instant.now().minus(5, ChronoUnit.DAYS));
        lenient().when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
        lenient().when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        return request;
    }

    private static BusinessException expectError(Runnable action) {
        try {
            action.run();
        } catch (BusinessException exception) {
            return exception;
        }
        throw new AssertionError("Mong đợi BusinessException");
    }

    // ---------- địa chỉ ----------

    @Test
    void confirmLogistics_whenSwapAndDonorHasNoAddress_throwsAddressRequired() {
        // given: Trao đổi cần cả hai địa chỉ, chủ bài chưa nhập
        Request request = new Request(ITEM_ID, RECEIVER_ID, OFFERED_ITEM_ID, "SWAP");
        request.select(Instant.now());
        request.proposeSchedule(
                "SHIPPING", null, Instant.now().plus(2, ChronoUnit.DAYS), false, true, Instant.now(), "12 Lê Lợi");

        // when / then
        assertThatThrownBy(() -> request.confirmLogistics(true, true, Instant.now(), null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(RequestErrorCode.REQUEST_SHIPPING_ADDRESS_REQUIRED));
        assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_LOGISTICS);
    }

    @Test
    void confirmLogistics_whenGiveAndOnlyReceiverHasAddress_confirmsSchedule() {
        // given: Cho tặng chỉ cần địa chỉ người nhận
        Request request = new Request(ITEM_ID, RECEIVER_ID, null, "GIVE");
        request.select(Instant.now());
        request.proposeSchedule("SHIPPING", null, Instant.now().plus(2, ChronoUnit.DAYS), true, true, Instant.now());

        // when
        request.confirmLogistics(false, true, Instant.now(), "12 Lê Lợi");

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
        assertThat(request.requiredLegs()).isEqualTo(1);
    }

    @Test
    void confirmLogistics_whenAddressChangedBeforeConfirm_clearsOtherPartyConfirmation() {
        // given: Trao đổi, chủ bài đã xác nhận lịch rồi người nhận đổi địa chỉ của mình
        Request request = new Request(ITEM_ID, RECEIVER_ID, OFFERED_ITEM_ID, "SWAP");
        request.select(Instant.now());
        request.proposeSchedule(
                "SHIPPING", null, Instant.now().plus(2, ChronoUnit.DAYS), false, true, Instant.now(), "12 Lê Lợi");
        request.proposeSchedule(
                "SHIPPING", null, Instant.now().plus(2, ChronoUnit.DAYS), true, true, Instant.now(), "34 Trần Phú");
        assertThat(request.getDonorScheduleOkAt()).isNotNull();

        // when: người nhận xác nhận kèm địa chỉ mới thì xác nhận của chủ bài bị xoá
        request.confirmLogistics(false, true, Instant.now(), "99 Hai Bà Trưng");

        // then
        assertThat(request.getDonorScheduleOkAt()).isNull();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_LOGISTICS);
        assertThat(request.getReceiverAddress()).isEqualTo("99 Hai Bà Trưng");
        assertThat(request.getScheduleRevision()).isEqualTo(3);
    }

    @Test
    void confirmLogistics_whenNotYetConfirmed_hidesOtherPartyAddressInResponse() {
        // given: người nhận đã nhập địa chỉ, chủ bài xem trước khi chốt lịch
        Request request = new Request(ITEM_ID, RECEIVER_ID, null, "GIVE");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        request.select(Instant.now());
        request.proposeSchedule(
                "SHIPPING", null, Instant.now().plus(2, ChronoUnit.DAYS), false, true, Instant.now(), "12 Lê Lợi");
        when(requestRepository.findByIdForScheduleUpdate(REQUEST_ID)).thenReturn(Optional.of(request));

        // when: chủ bài xác nhận lịch (chưa đủ cam kết của cả hai nên chưa chốt) rồi xem phản hồi
        RequestResponse asDonor =
                requestService.confirmLogistics(REQUEST_ID, DONOR_ID, new ConfirmLogisticsRequest(true, null, 1L));

        // then: lúc này đã chốt vì cả hai đồng ý, địa chỉ của người nhận hiện cho chủ bài
        assertThat(asDonor.status()).isEqualTo("LOGISTICS_CONFIRMED");
        assertThat(asDonor.shipping().receiverAddress()).isEqualTo("12 Lê Lợi");
    }

    @Test
    void updateSchedule_whenReceiverProposesShipping_donorDoesNotSeeAddressBeforeConfirm() {
        // given
        Request request = new Request(ITEM_ID, RECEIVER_ID, null, "GIVE");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        request.select(Instant.now());
        when(requestRepository.findByIdForScheduleUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
        lenient().when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        ScheduleRequest dto =
                new ScheduleRequest("SHIPPING", null, Instant.now().plus(2, ChronoUnit.DAYS), true, "12 Lê Lợi");

        // when
        RequestResponse asReceiver = requestService.updateSchedule(REQUEST_ID, RECEIVER_ID, dto);
        RequestResponse asDonor = requestService.getDetail(REQUEST_ID, DONOR_ID);

        // then: người nhận thấy địa chỉ của mình, chủ bài chưa thấy vì chưa chốt lịch
        assertThat(asReceiver.shipping().receiverAddress()).isEqualTo("12 Lê Lợi");
        assertThat(asDonor.shipping().receiverAddress()).isNull();
    }

    // ---------- gửi và nhận ----------

    @Test
    void confirmHandover_whenShippingLegNotShippedYet_throwsLegNotShipped() {
        // given
        shippingRequest(null, -1);

        // when / then
        assertThatThrownBy(
                        () -> requestService.confirmHandover(REQUEST_ID, RECEIVER_ID, new HandoverConfirmRequest(null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_LEG_NOT_SHIPPED));
    }

    @Test
    void markShipped_whenDonorShips_movesToAwaitingHandoverAndNotifiesReceiver() {
        // given
        Request request = shippingRequest(null, 0);

        // when
        requestService.markShipped(REQUEST_ID, DONOR_ID, new ShipmentRequest(null));

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_HANDOVER);
        assertThat(request.getDonorShippedAt()).isNotNull();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("SHIPMENT_SENT"), anyString(), body.capture(), anyString());
        assertThat(body.getValue()).contains("Người gửi báo đã gửi hàng", "Tôi đã nhận được hàng");
    }

    @Test
    void markShipped_whenSwapReceiverSendsWithPhoto_attachesPrivateEvidence() {
        Request request = shippingRequest(OFFERED_ITEM_ID, 0);
        var photos = List.of(42L);

        requestService.markShipped(REQUEST_ID, RECEIVER_ID, new ShipmentRequest(photos));

        assertThat(request.getReceiverShippedAt()).isNotNull();
        verify(mediaService).attachPrivate(photos, RECEIVER_ID);
    }

    @Test
    void markShipped_whenReceiverShipsInGive_throwsLegNotApplicable() {
        // given: Cho tặng chỉ có chiều 1, người nhận không có gì để gửi
        shippingRequest(null, 0);

        // when / then
        assertThat(expectError(() -> requestService.markShipped(REQUEST_ID, RECEIVER_ID, new ShipmentRequest(null)))
                        .getErrorCode())
                .isEqualTo(RequestErrorCode.REQUEST_LEG_NOT_APPLICABLE);
    }

    @Test
    void confirmHandover_whenSwapOneLegReceived_staysAwaitingHandover() {
        // given: chiều 1 đã gửi, người nhận (A) đã nhận, chiều 2 (B) chưa
        Request request = shippingRequest(OFFERED_ITEM_ID, 0);
        request.markShipped(true, Instant.now());

        // when
        RequestResponse response =
                requestService.confirmHandover(REQUEST_ID, RECEIVER_ID, new HandoverConfirmRequest(null));

        // then
        assertThat(response.status()).isEqualTo("AWAITING_HANDOVER");
        assertThat(request.isLegReceived(1)).isTrue();
        assertThat(request.isLegReceived(2)).isFalse();
        verify(itemService, never()).markTraded(any(Long.class));
    }

    @Test
    void confirmHandover_whenSwapBothLegsReceived_completesAndMarksBothItemsTraded() {
        // given
        Request request = shippingRequest(OFFERED_ITEM_ID, 0);
        request.markShipped(true, Instant.now());
        request.markShipped(false, Instant.now());
        request.markReceived(false, Instant.now());

        // when
        RequestResponse response =
                requestService.confirmHandover(REQUEST_ID, DONOR_ID, new HandoverConfirmRequest(null));

        // then
        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(itemService).markTraded(ITEM_ID);
        verify(itemService).markTraded(OFFERED_ITEM_ID);
        verify(reputationService).onCompleted(REQUEST_ID);
    }

    @Test
    void confirmHandover_whenGiveDonorTries_throwsLegNotApplicable() {
        // given: Cho tặng, chủ bài không có nút nhận hàng
        Request request = shippingRequest(null, 0);
        request.markShipped(true, Instant.now());

        // when / then
        assertThat(expectError(() ->
                                requestService.confirmHandover(REQUEST_ID, DONOR_ID, new HandoverConfirmRequest(null)))
                        .getErrorCode())
                .isEqualTo(RequestErrorCode.REQUEST_LEG_NOT_APPLICABLE);
    }

    // ---------- báo chưa nhận ----------

    @Test
    void handoverFailed_whenLegShipped_movesToDisputedWithItemAndReasonInNote() {
        // given: chiều 2 (B là chủ bài nhận) đã gửi nhưng chưa tới
        Request request = shippingRequest(OFFERED_ITEM_ID, -1);
        request.markShipped(false, Instant.now());

        // when
        RequestResponse response = requestService.handoverFailed(
                REQUEST_ID, DONOR_ID, new HandoverFailedRequest(CancelRequestCode.OTHER, "Chờ mãi không thấy", null));

        // then
        assertThat(response.status()).isEqualTo("DISPUTED");
        ArgumentCaptor<HandoverIssueReportedEvent> event = ArgumentCaptor.forClass(HandoverIssueReportedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().note()).contains("Chiều giao hàng món đổi", "Chờ mãi không thấy");
    }

    @Test
    void handoverFailed_whenOtherLegReceivedAndMyLegNotShipped_movesToDisputedImmediately() {
        // given: A (người nhận) đã nhận chiều 1, B (chủ bài) chưa nhận chiều 2 và A chưa gửi
        Request request = shippingRequest(OFFERED_ITEM_ID, 0);
        request.markShipped(true, Instant.now());
        request.markReceived(false, Instant.now());

        // when
        RequestResponse response = requestService.handoverFailed(
                REQUEST_ID,
                DONOR_ID,
                new HandoverFailedRequest(CancelRequestCode.OTHER, "Họ nhận rồi mà chưa gửi", null));

        // then
        assertThat(response.status()).isEqualTo("DISPUTED");
    }

    @Test
    void handoverFailed_whenLegNotShippedAndBeforeSendDeadline_throwsWithDeadlineMessage() {
        // given: ngày gửi dự kiến hôm nay, hạn gửi sau 3 ngày
        shippingRequest(null, 0);

        // when / then
        assertThatThrownBy(() -> requestService.handoverFailed(
                        REQUEST_ID,
                        RECEIVER_ID,
                        new HandoverFailedRequest(CancelRequestCode.COUNTERPART_NOT_SHIPPED, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_SEND_DEADLINE_NOT_PASSED);
                    assertThat(ex.getMessage()).startsWith("Bên kia chưa gửi hàng, hạn gửi đến ");
                });
    }

    @Test
    void handoverFailed_whenLegNotShippedAndPastSendDeadline_createsCancelRequestNotShipped() {
        // given: ngày gửi dự kiến cách đây 4 ngày, quá hạn gửi 3 ngày
        Request request = shippingRequest(null, -4);

        // when
        RequestResponse response = requestService.handoverFailed(
                REQUEST_ID,
                RECEIVER_ID,
                new HandoverFailedRequest(CancelRequestCode.COUNTERPART_NOT_SHIPPED, null, null));

        // then
        assertThat(response.status()).isEqualTo("LOGISTICS_CONFIRMED");
        assertThat(request.hasPendingCancelRequest()).isTrue();
        assertThat(request.getCancelRequestCode()).isEqualTo(CancelRequestCode.COUNTERPART_NOT_SHIPPED);
        verify(notificationService).notify(eq(DONOR_ID), eq("CANCEL_REQUESTED"), anyString(), anyString(), anyString());
    }

    @Test
    void markShipped_whenReceiverHadReportedNotShipped_withdrawsTheCancelRequest() {
        // given: người nhận báo chưa nhận quá hạn, bên gửi phản đối bằng cách bấm đã gửi
        Request request = shippingRequest(null, -4);
        requestService.handoverFailed(
                REQUEST_ID,
                RECEIVER_ID,
                new HandoverFailedRequest(CancelRequestCode.COUNTERPART_NOT_SHIPPED, null, null));
        assertThat(request.hasPendingCancelRequest()).isTrue();

        // when
        requestService.markShipped(REQUEST_ID, DONOR_ID, new ShipmentRequest(null));

        // then
        assertThat(request.hasPendingCancelRequest()).isFalse();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_HANDOVER);
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("CANCEL_REQUEST_WITHDRAWN"), anyString(), anyString(), anyString());
    }

    @Test
    void refuseHandover_whenLegNotShipped_throwsLegNotShipped() {
        // given
        shippingRequest(null, -1);

        // when / then
        assertThat(expectError(() -> requestService.refuseHandover(
                                REQUEST_ID, RECEIVER_ID, new RefuseHandoverRequest("Món không đúng mô tả", null)))
                        .getErrorCode())
                .isEqualTo(RequestErrorCode.REQUEST_LEG_NOT_SHIPPED);
    }

    @Test
    void handoverFailed_whenOnlyMyItemWasSent_opensDisputeWhileTransactionIsActive() {
        Request request = shippingRequest(OFFERED_ITEM_ID, -1);
        Instant cutoff = request.getMeetingTime();
        request.setExchangeShippingWindowStart(cutoff.minus(30, ChronoUnit.MINUTES));
        request.markShipped(true, cutoff.minusSeconds(1));

        RequestResponse response = requestService.handoverFailed(
                REQUEST_ID, DONOR_ID, new HandoverFailedRequest(CancelRequestCode.COUNTERPART_NOT_SHIPPED, null, null));

        assertThat(response.status()).isEqualTo("DISPUTED");
        assertThat(request.hasPendingCancelRequest()).isFalse();
    }

    // ---------- tự hoàn tất và kẹt một chiều ----------

    @Test
    void autoReceiveShippingLegs_whenOnlyOneLegWasShipped_doesNotConfirmReceipt() {
        // given: Trao đổi, chiều 1 gửi 8 ngày trước, chiều 2 chưa gửi
        Request request = shippingRequest(OFFERED_ITEM_ID, -9);
        request.markShipped(true, Instant.now().minus(8, ChronoUnit.DAYS));

        // when
        boolean changed = requestService.autoReceiveShippingLegs(REQUEST_ID, Instant.now());

        // then
        assertThat(changed).isFalse();
        assertThat(request.isLegReceived(1)).isFalse();
        assertThat(request.isLegReceived(2)).isFalse();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_HANDOVER);
    }

    @Test
    void autoReceiveShippingLegs_whenNothingShipped_neverCompletes() {
        // given
        Request request = shippingRequest(OFFERED_ITEM_ID, -20);

        // when
        boolean changed = requestService.autoReceiveShippingLegs(REQUEST_ID, Instant.now());

        // then
        assertThat(changed).isFalse();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
    }

    @Test
    void autoReceiveShippingLegs_whenAllLegsReceivedAutomatically_completesWithAutoConfirmedAndTwoDifferentSentences() {
        // given: Cho tặng, chiều 1 gửi 8 ngày trước
        Request request = shippingRequest(null, -9);
        request.markShipped(true, Instant.now().minus(8, ChronoUnit.DAYS));

        // when
        requestService.autoReceiveShippingLegs(REQUEST_ID, Instant.now());

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(request.isAutoConfirmed()).isTrue();
        ArgumentCaptor<String> silent = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> other = ArgumentCaptor.forClass(String.class);
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("TRANSACTION_COMPLETED"), anyString(), silent.capture(), anyString());
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("TRANSACTION_COMPLETED"), anyString(), other.capture(), anyString());
        assertThat(silent.getValue()).startsWith("Giao dịch đã tự hoàn tất vì bạn không phản hồi trong 7 ngày");
        assertThat(silent.getValue()).contains("Bạn có thể khiếu nại tới");
        assertThat(other.getValue()).contains("không phản hồi trong 7 ngày").doesNotContain("vì bạn không phản hồi");
    }

    @Test
    void exchangeShippingWindow_whenOnlyOnePartySent_cancelsAndHoldsBothItems() {
        Request request = shippingRequest(OFFERED_ITEM_ID, -1);
        Instant cutoff = request.getMeetingTime();
        request.setExchangeShippingWindowStart(cutoff.minus(30, ChronoUnit.MINUTES));
        request.markShipped(true, cutoff.minus(10, ChronoUnit.MINUTES));

        assertThat(requestService.processExchangeShippingWindow(REQUEST_ID, cutoff))
                .isTrue();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(request.exchangeItemReopenAt(1)).isEqualTo(cutoff.plus(24, ChronoUnit.HOURS));
        assertThat(request.exchangeItemReopenAt(2)).isNull();
        verify(itemService).holdForExchangeShipping(ITEM_ID, REQUEST_ID);
        verify(itemService).holdForExchangeShipping(OFFERED_ITEM_ID, REQUEST_ID);
        ArgumentCaptor<HandoverIssueReportedEvent> event = ArgumentCaptor.forClass(HandoverIssueReportedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().exchangeShippingTimeout()).isTrue();
        assertThat(event.getValue().reporterId()).isEqualTo(DONOR_ID);
        assertThat(event.getValue().affectedItemId()).isEqualTo(OFFERED_ITEM_ID);
        assertThat(requestService.processExchangeShippingWindow(REQUEST_ID, cutoff))
                .isFalse();
    }

    @Test
    void exchangeShippingWindow_whenNobodySent_cancelsAndReleasesBothItems() {
        Request request = shippingRequest(OFFERED_ITEM_ID, -1);
        Instant cutoff = request.getMeetingTime();
        request.setExchangeShippingWindowStart(cutoff.minus(30, ChronoUnit.MINUTES));

        assertThat(requestService.processExchangeShippingWindow(REQUEST_ID, cutoff))
                .isTrue();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        verify(itemService).releaseAfterCancel(eq(ITEM_ID), any());
        verify(itemService).releaseIfReservedAfterCancel(eq(OFFERED_ITEM_ID), any());
        verify(eventPublisher, never()).publishEvent(any(HandoverIssueReportedEvent.class));
    }

    @Test
    void exchangeShippingWindow_whenBothSent_leavesHandoverOpen() {
        Request request = shippingRequest(OFFERED_ITEM_ID, -1);
        Instant cutoff = request.getMeetingTime();
        request.setExchangeShippingWindowStart(cutoff.minus(30, ChronoUnit.MINUTES));
        request.markShipped(true, cutoff.minusSeconds(20));
        request.markShipped(false, cutoff.minusSeconds(10));

        assertThat(requestService.processExchangeShippingWindow(REQUEST_ID, cutoff))
                .isFalse();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_HANDOVER);
    }

    @Test
    void exchangeShippingShipment_requiresWindowAndReceipt() {
        Request request = shippingRequest(OFFERED_ITEM_ID, 1);
        request.setExchangeShippingWindowStart(request.getMeetingTime().minus(30, ChronoUnit.MINUTES));
        assertThat(expectError(() ->
                                requestService.markShipped(REQUEST_ID, DONOR_ID, new ShipmentRequest(List.of(42L))))
                        .getErrorCode())
                .isEqualTo(RequestErrorCode.REQUEST_EXCHANGE_SHIPPING_WINDOW_CLOSED);

        request.setExchangeShippingWindowStart(Instant.now().minusSeconds(1));
        assertThat(expectError(() -> requestService.markShipped(REQUEST_ID, DONOR_ID, new ShipmentRequest(null)))
                        .getErrorCode())
                .isEqualTo(RequestErrorCode.REQUEST_SHIPMENT_RECEIPT_REQUIRED);

        ReflectionTestUtils.setField(request, "meetingTime", Instant.now().minusSeconds(1));
        assertThat(expectError(() ->
                                requestService.markShipped(REQUEST_ID, DONOR_ID, new ShipmentRequest(List.of(42L))))
                        .getErrorCode())
                .isEqualTo(RequestErrorCode.REQUEST_EXCHANGE_SHIPPING_WINDOW_CLOSED);
        assertThat(request.isAnyLegShipped()).isFalse();
    }

    @Test
    void exchangeShippingSchedule_allowsWeekendAndInclusiveBoundsInVietnam() {
        var calculator = new RequestDeadlineCalculator(configService);
        Instant dayEarlier = Instant.parse("2026-11-27T00:00:00Z");
        for (String time : List.of("01:30", "05:00", "06:30", "10:00")) {
            Instant meeting = Instant.parse("2026-11-28T" + time + ":00Z");
            assertThat(calculator.exchangeShippingWindowStart(meeting, dayEarlier))
                    .isEqualTo(meeting.minus(30, ChronoUnit.MINUTES));
        }
        assertThat(expectError(() -> calculator.exchangeShippingWindowStart(
                                Instant.parse("2026-11-28T05:01:00Z"), dayEarlier))
                        .getErrorCode())
                .isEqualTo(RequestErrorCode.REQUEST_EXCHANGE_SHIPPING_HOURS);
        Instant meeting = Instant.parse("2026-11-28T01:30:00Z");
        assertThat(expectError(() ->
                                calculator.exchangeShippingWindowStart(meeting, meeting.minus(59, ChronoUnit.MINUTES)))
                        .getErrorCode())
                .isEqualTo(RequestErrorCode.REQUEST_EXCHANGE_SHIPPING_TOO_SOON);
    }

    @Test
    void confirmLogistics_whenGiftBecameExchange_rechecksTheRequiredLeadTime() {
        Instant now = Instant.now();
        Request request = new Request(ITEM_ID, RECEIVER_ID, null, "GIVE");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        request.select(now);
        request.proposeSchedule("SHIPPING", null, now.plus(30, ChronoUnit.MINUTES), false, true, now, "12 Lê Lợi");
        request.convertType("SWAP");
        ReflectionTestUtils.setField(request, "offeredItemId", OFFERED_ITEM_ID);
        when(requestRepository.findByIdForScheduleUpdate(REQUEST_ID)).thenReturn(Optional.of(request));

        ConfirmLogisticsRequest confirmation =
                new ConfirmLogisticsRequest(true, "34 Trần Phú", request.getScheduleRevision());
        assertThat(expectError(() -> requestService.confirmLogistics(REQUEST_ID, DONOR_ID, confirmation))
                        .getErrorCode())
                .isEqualTo(RequestErrorCode.REQUEST_EXCHANGE_SHIPPING_TOO_SOON);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_LOGISTICS);
    }

    @Test
    void exchangeShippingAutoReceipt_countsSevenDaysFromBothSentAndFirstReceipt() {
        Request request = shippingRequest(OFFERED_ITEM_ID, -20);
        Instant firstReceipt = Instant.now().minus(3, ChronoUnit.DAYS);
        request.markShipped(true, firstReceipt.minus(3, ChronoUnit.DAYS));
        request.markShipped(false, firstReceipt.minus(2, ChronoUnit.DAYS));

        assertThat(requestService.autoReceiveShippingLegs(REQUEST_ID, firstReceipt.plus(8, ChronoUnit.DAYS)))
                .isFalse();
        request.markReceived(false, firstReceipt);
        Instant due = firstReceipt.plus(7, ChronoUnit.DAYS);
        assertThat(requestService.autoReceiveShippingLegs(REQUEST_ID, due.minusSeconds(1)))
                .isFalse();
        assertThat(requestService.autoReceiveShippingLegs(REQUEST_ID, due)).isTrue();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
    }

    @Test
    void cancelStaleConfirmed_whenShippingNothingSentPastDeadlinePlusDays_cancelsWithoutPenalty() {
        // given: ngày gửi dự kiến cách đây 7 ngày, hạn gửi 3 ngày cộng chờ 3 ngày đã qua
        Request request = shippingRequest(null, -7);

        // when
        boolean cancelled = requestService.cancelStaleConfirmed(REQUEST_ID, Instant.now());

        // then
        assertThat(cancelled).isTrue();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        verify(itemService).releaseAfterCancel(eq(ITEM_ID), any());
        verify(reputationService, never()).onCompleted(any(Long.class));
    }

    @Test
    void remindShipping_whenRunTwice_sendsEachReminderOnlyOnce() {
        // given: quá hạn gửi, chiều 1 chưa gửi
        shippingRequest(null, -4);
        when(notificationService.hasNotificationSince(any(Long.class), anyString(), anyString(), any()))
                .thenReturn(false, false, false, true, true, true);

        // when
        boolean first = requestService.remindShipping(REQUEST_ID, Instant.now());
        boolean second = requestService.remindShipping(REQUEST_ID, Instant.now());

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("SHIP_DATE_REMINDER"), anyString(), anyString(), anyString());
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("SHIP_OVERDUE_REMINDER"), anyString(), anyString(), anyString());
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("SHIP_OVERDUE_NOTICE"), anyString(), anyString(), anyString());
    }

    @Test
    void sendDeadline_whenShipping_isMeetingTimePlusConfiguredDays() {
        // given
        Request request = shippingRequest(null, 1);
        RequestDeadlineCalculator calculator = new RequestDeadlineCalculator(configService);

        // when
        Instant deadline = calculator.sendDeadlineAt(request);

        // then
        assertThat(deadline).isEqualTo(request.getMeetingTime().plus(3, ChronoUnit.DAYS));
        assertThat(List.of(request.requiredLegs())).containsExactly(1);
    }
}
