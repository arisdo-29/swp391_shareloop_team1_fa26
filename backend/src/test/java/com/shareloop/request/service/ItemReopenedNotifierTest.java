package com.shareloop.request.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.notification.service.NotificationService;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.repository.RequestRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemReopenedNotifierTest {

    @Mock
    RequestRepository requestRepository;

    @Mock
    NotificationService notificationService;

    @InjectMocks
    ItemReopenedNotifier itemReopenedNotifier;

    @Test
    void notifyRejectedRequesters_whenSameUserRejectedTwice_notifiesEachUserOnce() {
        // given: người 11 bị từ chối hai lần, người 12 một lần
        when(requestRepository.findByItemIdAndStatus(1L, RequestStatus.REJECTED))
                .thenReturn(List.of(rejected(11L), rejected(11L), rejected(12L)));

        // when
        itemReopenedNotifier.notifyRejectedRequesters(1L, 10L);

        // then
        verify(notificationService, times(1))
                .notify(
                        eq(11L),
                        eq("REQUESTED_ITEM_REOPENED"),
                        eq("Món bạn từng yêu cầu đã mở lại"),
                        anyString(),
                        eq("/items/1"));
        verify(notificationService, times(1))
                .notify(
                        eq(12L),
                        eq("REQUESTED_ITEM_REOPENED"),
                        eq("Món bạn từng yêu cầu đã mở lại"),
                        anyString(),
                        eq("/items/1"));
    }

    @Test
    void notifyRejectedRequesters_skipsReceiverOfCancelledTransaction() {
        // given: người nhận của giao dịch vừa huỷ từng bị từ chối ở lần trước
        when(requestRepository.findByItemIdAndStatus(1L, RequestStatus.REJECTED))
                .thenReturn(List.of(rejected(10L)));

        // when
        itemReopenedNotifier.notifyRejectedRequesters(1L, 10L);

        // then
        verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any());
    }

    private static Request rejected(long receiverId) {
        Request request = new Request(1L, receiverId, null, "GIVE");
        request.reject();
        return request;
    }
}
