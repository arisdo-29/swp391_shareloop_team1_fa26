package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
class RequestAdminCancelTest {

    private static final long REQUEST_ID = 101L;
    private static final long ADMIN_ID = 1L;
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
    }

    private Request request(RequestStatus status, Long offeredItemId) {
        Request request = new Request(1L, RECEIVER_ID, offeredItemId, offeredItemId == null ? "GIVE" : "SWAP");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", status);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        return request;
    }

    @Test
    @DisplayName("Admin huỷ giao dịch DISPUTED: CANCELLED, nhả hai món, thông báo cả hai bên, ghi log do Admin")
    void cancelByAdmin_whenDisputedSwap_cancelsReleasesNotifiesBothAndLogs() {
        // given
        Request request = request(RequestStatus.DISPUTED, 50L);
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));
        when(itemQueryService.getSummary(1L)).thenReturn(new ItemSummary(1L, DONOR_ID, null, null));

        // when
        requestService.cancelByAdmin(
                REQUEST_ID, ADMIN_ID, new CancelTransactionRequest("  Một bên bị khoá tài khoản "));

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(request.getCancelReason()).isEqualTo("[Admin] Một bên bị khoá tài khoản");
        verify(itemService).release(1L);
        verify(itemService).releaseIfReserved(50L);
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
        verify(activityLogService)
                .log(
                        eq("REQUEST_CANCEL"),
                        eq(ADMIN_ID),
                        eq("REQUEST"),
                        eq(REQUEST_ID),
                        (String) isNull(),
                        eq("[Admin] Một bên bị khoá tài khoản"));
    }

    @Test
    @DisplayName("Admin huỷ giao dịch đã kết thúc hoặc chưa chọn người thì 409")
    void cancelByAdmin_whenStatusNotCancellable_throwsInvalidStateTransition() {
        for (RequestStatus status : new RequestStatus[] {
            RequestStatus.PENDING, RequestStatus.COMPLETED, RequestStatus.CANCELLED, RequestStatus.REJECTED
        }) {
            // given
            request(status, null);

            // when / then
            assertThatThrownBy(() -> requestService.cancelByAdmin(
                            REQUEST_ID, ADMIN_ID, new CancelTransactionRequest("Không còn cần thiết")))
                    .as("trạng thái %s", status)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            ex -> assertThat(ex.getErrorCode())
                                    .isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION));
        }
        verify(itemService, never()).release(1L);
    }
}
