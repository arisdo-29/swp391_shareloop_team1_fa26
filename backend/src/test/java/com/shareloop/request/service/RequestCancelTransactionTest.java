package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.CancelTransactionRequest;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RequestCancelTransactionTest {

    private static final long REQUEST_ID = 101L;
    private static final long ITEM_ID = 1L;
    private static final long OFFERED_ITEM_ID = 50L;
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
    ActivityLogService activityLogService;

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
                null,
                activityLogService);
        when(itemQueryService.getSummary(ITEM_ID)).thenReturn(new ItemSummary(ITEM_ID, DONOR_ID, null, null));
    }

    private Request requestInStatus(RequestStatus status, Long offeredItemId) {
        Request request = new Request(ITEM_ID, RECEIVER_ID, offeredItemId, offeredItemId == null ? "GIVE" : "SWAP");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", status);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        return request;
    }

    @Test
    @DisplayName("Chủ bài huỷ giao dịch: Request CANCELLED, lý do có tiền tố, thông báo người nhận, ghi activity log")
    void cancelTransaction_whenDonorCancels_cancelsNotifiesAndLogs() {
        // given
        Request request = requestInStatus(RequestStatus.AWAITING_LOGISTICS, null);
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));

        // when
        RequestResponse response = requestService.cancelTransaction(
                REQUEST_ID, DONOR_ID, new CancelTransactionRequest("  Tôi bận việc đột xuất  "));

        // then
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(request.getCancelReason()).isEqualTo("[Chủ bài] Tôi bận việc đột xuất");
        verify(itemService).release(ITEM_ID);
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("TRANSACTION_CANCELLED"), anyString(), contains("Tôi bận"), anyString());
        verify(activityLogService)
                .log(
                        eq("REQUEST_CANCEL"),
                        eq(DONOR_ID),
                        eq("REQUEST"),
                        eq(REQUEST_ID),
                        (String) isNull(),
                        eq("[Chủ bài] Tôi bận việc đột xuất"));
    }

    @Test
    @DisplayName("Người nhận huỷ giao dịch: lý do có tiền tố người nhận, thông báo chủ bài")
    void cancelTransaction_whenReceiverCancels_prefixesReasonAndNotifiesDonor() {
        // given
        Request request = requestInStatus(RequestStatus.LOGISTICS_CONFIRMED, null);
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));

        // when
        requestService.cancelTransaction(REQUEST_ID, RECEIVER_ID, new CancelTransactionRequest("Không đi được nữa"));

        // then
        assertThat(request.getCancelReason()).isEqualTo("[Người nhận] Không đi được nữa");
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Huỷ giao dịch SWAP đang AWAITING_HANDOVER: nhả cả bài chính và món mang đổi")
    void cancelTransaction_whenSwapAwaitingHandover_releasesBothItems() {
        // given
        Request request = requestInStatus(RequestStatus.AWAITING_HANDOVER, OFFERED_ITEM_ID);
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));

        // when
        requestService.cancelTransaction(REQUEST_ID, RECEIVER_ID, new CancelTransactionRequest("Đối phương không đến"));

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        verify(itemService).release(ITEM_ID);
        verify(itemService).releaseIfReserved(OFFERED_ITEM_ID);
    }

    @Test
    @DisplayName("Người ngoài giao dịch huỷ thì bị REQUEST_FORBIDDEN")
    void cancelTransaction_whenOutsider_throwsForbidden() {
        // given
        requestInStatus(RequestStatus.RESERVED, null);

        // when / then
        assertThatThrownBy(() -> requestService.cancelTransaction(
                        REQUEST_ID, OUTSIDER_ID, new CancelTransactionRequest("Tôi muốn huỷ")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_FORBIDDEN));
        verify(itemService, never()).release(ITEM_ID);
    }

    @Test
    @DisplayName("Trạng thái không cho huỷ (PENDING, COMPLETED, DISPUTED, CANCELLED, REJECTED) thì 409")
    void cancelTransaction_whenStatusNotCancellable_throwsInvalidStateTransition() {
        RequestStatus[] blockedStatuses = {
            RequestStatus.PENDING,
            RequestStatus.COMPLETED,
            RequestStatus.DISPUTED,
            RequestStatus.CANCELLED,
            RequestStatus.REJECTED
        };
        for (RequestStatus status : blockedStatuses) {
            // given
            requestInStatus(status, null);

            // when / then
            assertThatThrownBy(() -> requestService.cancelTransaction(
                            REQUEST_ID, DONOR_ID, new CancelTransactionRequest("Tôi muốn huỷ")))
                    .as("trạng thái %s", status)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            ex -> assertThat(ex.getErrorCode())
                                    .isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION));
        }
        verify(itemService, never()).release(ITEM_ID);
    }
}
