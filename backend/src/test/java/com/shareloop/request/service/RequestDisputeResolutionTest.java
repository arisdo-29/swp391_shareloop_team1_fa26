package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.reputation.service.ReputationService;
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
class RequestDisputeResolutionTest {

    private static final long REQUEST_ID = 101L;
    private static final long ITEM_ID = 1L;
    private static final long OFFERED_ITEM_ID = 50L;
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
                null,
                null);
    }

    private Request disputedSwapRequest() {
        Request request = new Request(ITEM_ID, RECEIVER_ID, OFFERED_ITEM_ID, "SWAP");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", RequestStatus.DISPUTED);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(itemQueryService.getSummary(ITEM_ID)).thenReturn(new ItemSummary(ITEM_ID, DONOR_ID, null, null));
        return request;
    }

    @Test
    @DisplayName("Chấp nhận khiếu nại: Request CANCELLED, nhả cả hai món, thông báo cả hai bên")
    void resolveDisputedRequest_whenUpheld_cancelsReleasesBothItemsAndNotifiesBoth() {
        // given
        Request request = disputedSwapRequest();

        // when
        boolean resolved = requestService.resolveDisputedRequest(REQUEST_ID, true);

        // then
        assertThat(resolved).isTrue();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(request.getCancelReason()).startsWith("[Hệ thống] ");
        verify(itemService).releaseIfReserved(ITEM_ID);
        verify(itemService).releaseIfReserved(OFFERED_ITEM_ID);
        verify(itemService, never()).markTraded(org.mockito.ArgumentMatchers.anyLong());
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Bác bỏ khiếu nại: Request COMPLETED, hai món TRADED, cộng điểm uy tín, thông báo cả hai bên")
    void resolveDisputedRequest_whenDismissed_completesMarksTradedAndAwardsPoints() {
        // given
        Request request = disputedSwapRequest();

        // when
        boolean resolved = requestService.resolveDisputedRequest(REQUEST_ID, false);

        // then
        assertThat(resolved).isTrue();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(request.getCompletedAt()).isNotNull();
        verify(itemService).markTraded(ITEM_ID);
        verify(itemService).markTraded(OFFERED_ITEM_ID);
        verify(reputationService).onCompleted(REQUEST_ID);
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("TRANSACTION_COMPLETED"), anyString(), anyString(), anyString());
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("TRANSACTION_COMPLETED"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Giao dịch đã COMPLETED (khiếu nại mở sau hoàn tất) thì không bị đổi gì")
    void resolveDisputedRequest_whenNotDisputed_doesNothing() {
        // given
        Request request = new Request(ITEM_ID, RECEIVER_ID, null, "GIVE");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", RequestStatus.COMPLETED);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        // when
        boolean resolved = requestService.resolveDisputedRequest(REQUEST_ID, true);

        // then
        assertThat(resolved).isFalse();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        verify(itemService, never()).releaseIfReserved(org.mockito.ArgumentMatchers.anyLong());
        verify(notificationService, never()).notify(any(Long.class), any(), any(), any(), any());
    }
}
