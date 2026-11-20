package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.CancelTransactionRequest;
import com.shareloop.request.dto.HandoverFailedRequest;
import com.shareloop.request.dto.RefuseHandoverRequest;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.event.HandoverIssueReportedEvent;
import com.shareloop.request.event.NotAsDescribedReportedEvent;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** Ba nút kết quả giao nhận: handover-failed (huỷ hoặc tranh chấp) và refuse-handover (GN-06, GN-07). */
@ExtendWith(MockitoExtension.class)
class RequestHandoverOutcomeTest {

    private static final long REQUEST_ID = 101L;
    private static final long ITEM_ID = 1L;
    private static final long OFFERED_ITEM_ID = 2L;
    private static final long DONOR_ID = 20L;
    private static final long RECEIVER_ID = 10L;
    private static final long OUTSIDER_ID = 99L;

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
        when(itemQueryService.getSummary(ITEM_ID)).thenReturn(new ItemSummary(ITEM_ID, DONOR_ID, null, null));
    }

    /** Tạo giao dịch Cho tặng ở trạng thái status với thời điểm bấm hoàn tất của từng bên cho trước. */
    private Request giveRequest(RequestStatus status, Instant donorHandoverAt, Instant receiverHandoverAt) {
        return request(null, "GIVE", status, donorHandoverAt, receiverHandoverAt);
    }

    /** Tạo giao dịch Trao đổi (có món đổi) ở trạng thái status, chưa bên nào bấm hoàn tất. */
    private Request swapRequest(RequestStatus status) {
        return request(OFFERED_ITEM_ID, "SWAP", status, null, null);
    }

    private Request request(
            Long offeredItemId,
            String type,
            RequestStatus status,
            Instant donorHandoverAt,
            Instant receiverHandoverAt) {
        Request request = new Request(ITEM_ID, RECEIVER_ID, offeredItemId, type);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", status);
        ReflectionTestUtils.setField(request, "donorHandoverAt", donorHandoverAt);
        ReflectionTestUtils.setField(request, "receiverHandoverAt", receiverHandoverAt);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        return request;
    }

    private void saveReturnsArgument() {
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---------- handover-failed ----------

    @Test
    @DisplayName("Giao nhận không thành công khi bên kia chưa bấm hoàn tất: huỷ giao dịch, mở lại bài, báo bên kia")
    void handoverFailed_whenOtherPartyNotConfirmed_cancelsAndReleasesItem() {
        // given
        Request request = giveRequest(RequestStatus.LOGISTICS_CONFIRMED, null, null);
        saveReturnsArgument();

        // when
        RequestResponse response = requestService.handoverFailed(
                REQUEST_ID, RECEIVER_ID, new HandoverFailedRequest("  Không đến điểm hẹn  ", null));

        // then
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(request.getCancelReason()).isEqualTo(Request.CANCEL_BY_RECEIVER + "Không đến điểm hẹn");
        verify(itemService).release(ITEM_ID);
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
        verify(eventPublisher, never()).publishEvent(any(HandoverIssueReportedEvent.class));
    }

    @Test
    @DisplayName("Giao nhận không thành công khi bên kia đã bấm hoàn tất: sang DISPUTED và mở khiếu nại NO_SHOW")
    void handoverFailed_whenOtherPartyConfirmed_movesToDisputedAndPublishesEvent() {
        // given
        Request request = giveRequest(RequestStatus.AWAITING_HANDOVER, Instant.now(), null);
        saveReturnsArgument();

        // when
        RequestResponse response = requestService.handoverFailed(
                REQUEST_ID, RECEIVER_ID, new HandoverFailedRequest("  Chưa thấy ai giao đồ  ", List.of(7L)));

        // then
        assertThat(response.status()).isEqualTo("DISPUTED");
        assertThat(request.getStatus()).isEqualTo(RequestStatus.DISPUTED);
        ArgumentCaptor<HandoverIssueReportedEvent> captor = ArgumentCaptor.forClass(HandoverIssueReportedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(
                        new HandoverIssueReportedEvent(REQUEST_ID, RECEIVER_ID, "Chưa thấy ai giao đồ", List.of(7L)));
        verify(itemService, never()).release(ITEM_ID);
    }

    @Test
    @DisplayName("Giao nhận không thành công khi chính mình đã bấm hoàn tất thì 409")
    void handoverFailed_whenReporterAlreadyConfirmed_throwsInvalidStateTransition() {
        // given
        giveRequest(RequestStatus.AWAITING_HANDOVER, null, Instant.now());

        // when / then
        assertThatThrownBy(() -> requestService.handoverFailed(
                        REQUEST_ID, RECEIVER_ID, new HandoverFailedRequest("Không đến điểm hẹn", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION));
    }

    @Test
    @DisplayName("Giao nhận không thành công khi chưa chốt lịch thì 409")
    void handoverFailed_whenNotYetConfirmedLogistics_throwsInvalidStateTransition() {
        // given
        giveRequest(RequestStatus.AWAITING_LOGISTICS, null, null);

        // when / then
        assertThatThrownBy(() -> requestService.handoverFailed(
                        REQUEST_ID, DONOR_ID, new HandoverFailedRequest("Không đến điểm hẹn", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION));
    }

    @Test
    @DisplayName("Người ngoài giao dịch báo giao nhận không thành công thì REQUEST_FORBIDDEN")
    void handoverFailed_whenOutsider_throwsForbidden() {
        // given
        giveRequest(RequestStatus.AWAITING_HANDOVER, Instant.now(), null);

        // when / then
        assertThatThrownBy(() -> requestService.handoverFailed(
                        REQUEST_ID, OUTSIDER_ID, new HandoverFailedRequest("Ghi chú sự cố", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_FORBIDDEN));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // ---------- cancel-transaction chỉ còn trước khi chốt lịch ----------

    @Test
    @DisplayName("Huỷ giao dịch sau khi đã chốt lịch thì 409, phải dùng giao nhận không thành công")
    void cancelTransaction_whenLogisticsConfirmed_throwsInvalidStateTransition() {
        // given
        giveRequest(RequestStatus.LOGISTICS_CONFIRMED, null, null);

        // when / then
        assertThatThrownBy(() -> requestService.cancelTransaction(
                        REQUEST_ID, RECEIVER_ID, new CancelTransactionRequest("Tôi đổi ý rồi")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION));
    }

    // ---------- refuse-handover ----------

    @Test
    @DisplayName("Người nhận ở Cho tặng từ chối nhận: huỷ ngay, ẩn bài chính, báo cáo nhắm vào chủ bài")
    void refuseHandover_whenGiveReceiverRefuses_cancelsHidesMainItemAndReportsDonor() {
        // given
        Request request = giveRequest(RequestStatus.LOGISTICS_CONFIRMED, null, null);
        saveReturnsArgument();

        // when
        RequestResponse response = requestService.refuseHandover(
                REQUEST_ID, RECEIVER_ID, new RefuseHandoverRequest("  Máy không bật được  ", List.of(5L)));

        // then
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(request.getCancelReason())
                .isEqualTo(Request.CANCEL_BY_RECEIVER + Request.REFUSAL_REASON + "Máy không bật được");
        verify(itemService).release(ITEM_ID);
        verify(itemService).hide(ITEM_ID);
        verify(itemService, never()).markTraded(org.mockito.ArgumentMatchers.anyLong());
        verify(eventPublisher)
                .publishEvent(new NotAsDescribedReportedEvent(
                        REQUEST_ID, RECEIVER_ID, ITEM_ID, DONOR_ID, "Máy không bật được", List.of(5L)));
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Chủ bài ở Cho tặng không được từ chối giao nhận")
    void refuseHandover_whenGiveDonorRefuses_throwsRefuseNotAllowed() {
        // given
        giveRequest(RequestStatus.AWAITING_HANDOVER, null, null);

        // when / then
        assertThatThrownBy(() -> requestService.refuseHandover(
                        REQUEST_ID, DONOR_ID, new RefuseHandoverRequest("Không muốn cho nữa", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_REFUSE_NOT_ALLOWED));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Chủ bài ở Trao đổi từ chối món đổi: huỷ ngay, bài chính mở lại, ẩn món đổi, báo cáo người xin")
    void refuseHandover_whenSwapDonorRefuses_reopensMainItemAndHidesOfferedItem() {
        // given
        Request request = swapRequest(RequestStatus.AWAITING_HANDOVER);
        saveReturnsArgument();

        // when
        requestService.refuseHandover(REQUEST_ID, DONOR_ID, new RefuseHandoverRequest("Món đổi bị nứt màn hình", null));

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        verify(itemService).release(ITEM_ID);
        verify(itemService).releaseIfReserved(OFFERED_ITEM_ID);
        verify(itemService).hide(OFFERED_ITEM_ID);
        verify(itemService, never()).hide(ITEM_ID);
        verify(eventPublisher)
                .publishEvent(new NotAsDescribedReportedEvent(
                        REQUEST_ID, DONOR_ID, OFFERED_ITEM_ID, RECEIVER_ID, "Món đổi bị nứt màn hình", null));
    }

    @Test
    @DisplayName("Người nhận ở Trao đổi từ chối bài chính: huỷ ngay, ẩn bài chính, món đổi mở lại")
    void refuseHandover_whenSwapReceiverRefuses_hidesMainItemAndReopensOfferedItem() {
        // given
        Request request = swapRequest(RequestStatus.LOGISTICS_CONFIRMED);
        saveReturnsArgument();

        // when
        requestService.refuseHandover(REQUEST_ID, RECEIVER_ID, new RefuseHandoverRequest("Máy không bật được", null));

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        verify(itemService).release(ITEM_ID);
        verify(itemService).hide(ITEM_ID);
        verify(itemService).releaseIfReserved(OFFERED_ITEM_ID);
        verify(itemService, never()).hide(OFFERED_ITEM_ID);
        verify(eventPublisher)
                .publishEvent(new NotAsDescribedReportedEvent(
                        REQUEST_ID, RECEIVER_ID, ITEM_ID, DONOR_ID, "Máy không bật được", null));
    }

    @Test
    @DisplayName("Đã bấm hoàn tất rồi thì không được từ chối giao nhận")
    void refuseHandover_whenAlreadyConfirmed_throwsInvalidStateTransition() {
        // given
        giveRequest(RequestStatus.AWAITING_HANDOVER, null, Instant.now());

        // when / then
        assertThatThrownBy(() -> requestService.refuseHandover(
                        REQUEST_ID, RECEIVER_ID, new RefuseHandoverRequest("Máy không bật được", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION));
    }
}
