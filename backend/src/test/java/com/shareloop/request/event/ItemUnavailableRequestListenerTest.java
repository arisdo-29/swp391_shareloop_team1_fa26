package com.shareloop.request.event;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shareloop.item.event.ItemUnavailableEvent;
import com.shareloop.request.service.RequestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemUnavailableRequestListenerTest {

    @Mock
    RequestService requestService;

    @InjectMocks
    ItemUnavailableRequestListener listener;

    @ParameterizedTest
    @EnumSource(ItemUnavailableEvent.Reason.class)
    void onItemUnavailable_forEveryReason_cancelsPendingRequestsOfferingItem(ItemUnavailableEvent.Reason reason) {
        // when
        listener.onItemUnavailable(new ItemUnavailableEvent(50L, reason));

        // then
        verify(requestService).cancelPendingRequestsOfferingItem(50L);
    }

    @Test
    void onItemUnavailable_whenEdited_notifiesPendingRequesters() {
        // when
        listener.onItemUnavailable(new ItemUnavailableEvent(50L, ItemUnavailableEvent.Reason.EDITED));

        // then
        verify(requestService).notifyPendingRequestersItemEdited(50L);
    }

    @Test
    void onItemUnavailable_whenReserved_doesNotSendEditedNotice() {
        // when
        listener.onItemUnavailable(new ItemUnavailableEvent(50L, ItemUnavailableEvent.Reason.RESERVED));

        // then
        verify(requestService, never()).notifyPendingRequestersItemEdited(anyLong());
    }
}
