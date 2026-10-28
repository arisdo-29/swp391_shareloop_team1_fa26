package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.HandoverIssueRequest;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.event.HandoverIssueReportedEvent;
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

@ExtendWith(MockitoExtension.class)
class RequestHandoverIssueTest {

    private static final long REQUEST_ID = 101L;
    private static final long ITEM_ID = 1L;
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

    /** Tạo Request đã ở trạng thái status với thời điểm xác nhận bàn giao của từng bên cho trước. */
    private Request request(RequestStatus status, Instant donorHandoverAt, Instant receiverHandoverAt) {
        Request request = new Request(ITEM_ID, RECEIVER_ID, null, "GIVE");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", status);
        ReflectionTestUtils.setField(request, "donorHandoverAt", donorHandoverAt);
        ReflectionTestUtils.setField(request, "receiverHandoverAt", receiverHandoverAt);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        return request;
    }

    @Test
    @DisplayName("Người nhận báo chưa nhận được khi chủ bài đã xác nhận: sang DISPUTED và phát event mở khiếu nại")
    void reportHandoverIssue_whenReceiverReportsAfterDonorConfirmed_movesToDisputedAndPublishesEvent() {
        // given
        Request request = request(RequestStatus.AWAITING_HANDOVER, Instant.now(), null);
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));

        // when
        RequestResponse response = requestService.reportHandoverIssue(
                REQUEST_ID, RECEIVER_ID, new HandoverIssueRequest("  Chưa thấy ai giao đồ  ", List.of(7L)));

        // then
        assertThat(response.status()).isEqualTo("DISPUTED");
        assertThat(request.getStatus()).isEqualTo(RequestStatus.DISPUTED);
        ArgumentCaptor<HandoverIssueReportedEvent> captor = ArgumentCaptor.forClass(HandoverIssueReportedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(
                        new HandoverIssueReportedEvent(REQUEST_ID, RECEIVER_ID, "Chưa thấy ai giao đồ", List.of(7L)));
    }

    @Test
    @DisplayName("Chủ bài báo chưa giao được khi người nhận đã xác nhận: sang DISPUTED")
    void reportHandoverIssue_whenDonorReportsAfterReceiverConfirmed_movesToDisputed() {
        // given
        Request request = request(RequestStatus.AWAITING_HANDOVER, null, Instant.now());
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));

        // when
        requestService.reportHandoverIssue(
                REQUEST_ID, DONOR_ID, new HandoverIssueRequest("Người nhận không đến lấy", null));

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.DISPUTED);
        verify(eventPublisher).publishEvent(any(HandoverIssueReportedEvent.class));
    }

    @Test
    @DisplayName("Bên kia chưa xác nhận bàn giao thì 409")
    void reportHandoverIssue_whenOtherSideNotConfirmed_throwsInvalidStateTransition() {
        // given
        request(RequestStatus.AWAITING_HANDOVER, null, null);

        // when / then
        assertInvalidTransition(RECEIVER_ID);
    }

    @Test
    @DisplayName("Chính mình đã xác nhận bàn giao rồi thì 409")
    void reportHandoverIssue_whenReporterAlreadyConfirmed_throwsInvalidStateTransition() {
        // given
        request(RequestStatus.AWAITING_HANDOVER, Instant.now(), Instant.now());

        // when / then
        assertInvalidTransition(RECEIVER_ID);
    }

    @Test
    @DisplayName("Trạng thái khác AWAITING_HANDOVER (kể cả LOGISTICS_CONFIRMED) thì 409")
    void reportHandoverIssue_whenStatusNotAwaitingHandover_throwsInvalidStateTransition() {
        for (RequestStatus status : new RequestStatus[] {
            RequestStatus.LOGISTICS_CONFIRMED, RequestStatus.COMPLETED, RequestStatus.DISPUTED, RequestStatus.CANCELLED
        }) {
            // given
            request(status, Instant.now(), null);

            // when / then
            assertInvalidTransition(RECEIVER_ID);
        }
    }

    @Test
    @DisplayName("Người ngoài giao dịch báo sự cố thì REQUEST_FORBIDDEN")
    void reportHandoverIssue_whenOutsider_throwsForbidden() {
        // given
        request(RequestStatus.AWAITING_HANDOVER, Instant.now(), null);

        // when / then
        assertThatThrownBy(() -> requestService.reportHandoverIssue(
                        REQUEST_ID, OUTSIDER_ID, new HandoverIssueRequest("Ghi chú sự cố", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_FORBIDDEN));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private void assertInvalidTransition(long reporterId) {
        assertThatThrownBy(() -> requestService.reportHandoverIssue(
                        REQUEST_ID, reporterId, new HandoverIssueRequest("Ghi chú sự cố", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
