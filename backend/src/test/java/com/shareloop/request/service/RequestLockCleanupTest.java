package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RequestLockCleanupTest {

    private static final long LOCKED_USER_ID = 10L;
    private static final long OTHER_USER_ID = 20L;

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
                null);
    }

    private Request request(long id, long itemId, long receiverId, Long offeredItemId, RequestStatus status) {
        Request request = new Request(itemId, receiverId, offeredItemId, offeredItemId == null ? "GIVE" : "SWAP");
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "status", status);
        return request;
    }

    private void itemOwnedBy(long itemId, long donorId) {
        when(itemQueryService.getSummary(itemId)).thenReturn(new ItemSummary(itemId, donorId, null, null));
    }

    @Test
    @DisplayName("Người bị khoá là người nhận: huỷ giao dịch SWAP đang mở, nhả cả hai món, thông báo chủ bài")
    void cancelOpenRequestsOfLockedUser_whenLockedUserIsReceiver_cancelsReleasesAndNotifiesDonor() {
        // given
        Request reserved = request(1L, 100L, LOCKED_USER_ID, 200L, RequestStatus.RESERVED);
        when(requestRepository.findByReceiverIdAndStatusIn(eq(LOCKED_USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(reserved));
        when(itemQueryService.findItemIdsByDonor(LOCKED_USER_ID)).thenReturn(List.of());
        itemOwnedBy(100L, OTHER_USER_ID);

        // when
        int cancelled = requestService.cancelOpenRequestsOfLockedUser(LOCKED_USER_ID);

        // then
        assertThat(cancelled).isEqualTo(1);
        assertThat(reserved.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(reserved.getCancelReason()).isEqualTo("[Hệ thống] Tài khoản bị khoá");
        verify(itemService).releaseIfReserved(100L);
        verify(itemService).releaseIfReserved(200L);
        verify(notificationService)
                .notify(eq(OTHER_USER_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Người bị khoá là chủ bài: huỷ giao dịch trên bài của họ, thông báo người nhận")
    void cancelOpenRequestsOfLockedUser_whenLockedUserIsDonor_cancelsAndNotifiesReceiver() {
        // given
        Request awaitingHandover = request(2L, 100L, OTHER_USER_ID, null, RequestStatus.AWAITING_HANDOVER);
        when(requestRepository.findByReceiverIdAndStatusIn(eq(LOCKED_USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(itemQueryService.findItemIdsByDonor(LOCKED_USER_ID)).thenReturn(List.of(100L));
        when(requestRepository.findByItemIdInAndStatusIn(eq(List.of(100L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(awaitingHandover));
        itemOwnedBy(100L, LOCKED_USER_ID);

        // when
        int cancelled = requestService.cancelOpenRequestsOfLockedUser(LOCKED_USER_ID);

        // then
        assertThat(cancelled).isEqualTo(1);
        assertThat(awaitingHandover.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        verify(itemService).releaseIfReserved(100L);
        verify(notificationService)
                .notify(eq(OTHER_USER_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Yêu cầu PENDING bị huỷ nhưng không nhả món nào vì chưa giữ chỗ")
    void cancelOpenRequestsOfLockedUser_whenRequestPending_cancelsWithoutReleasingItems() {
        // given
        Request pending = request(3L, 100L, LOCKED_USER_ID, null, RequestStatus.PENDING);
        when(requestRepository.findByReceiverIdAndStatusIn(eq(LOCKED_USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(pending));
        when(itemQueryService.findItemIdsByDonor(LOCKED_USER_ID)).thenReturn(List.of());
        itemOwnedBy(100L, OTHER_USER_ID);

        // when
        int cancelled = requestService.cancelOpenRequestsOfLockedUser(LOCKED_USER_ID);

        // then
        assertThat(cancelled).isEqualTo(1);
        assertThat(pending.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        verify(itemService, never()).releaseIfReserved(anyLong());
    }

    @Test
    @DisplayName("Không có giao dịch đang mở thì trả 0 và không thông báo ai")
    void cancelOpenRequestsOfLockedUser_whenNothingOpen_returnsZero() {
        // given
        when(requestRepository.findByReceiverIdAndStatusIn(eq(LOCKED_USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(itemQueryService.findItemIdsByDonor(LOCKED_USER_ID)).thenReturn(List.of());

        // when
        int cancelled = requestService.cancelOpenRequestsOfLockedUser(LOCKED_USER_ID);

        // then
        assertThat(cancelled).isZero();
        verify(notificationService, never()).notify(anyLong(), anyString(), anyString(), anyString(), anyString());
    }
}
