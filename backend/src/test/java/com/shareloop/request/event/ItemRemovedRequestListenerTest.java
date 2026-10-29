package com.shareloop.request.event;

import static org.mockito.Mockito.verify;

import com.shareloop.item.event.ItemRemovedEvent;
import com.shareloop.request.service.RequestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemRemovedRequestListenerTest {

    @Mock
    RequestService requestService;

    @InjectMocks
    ItemRemovedRequestListener listener;

    @Test
    void onItemRemoved_callsCancelPendingRequestsForItemRemoved() {
        // given
        ItemRemovedEvent event = new ItemRemovedEvent(100L, 10L);

        // when
        listener.onItemRemoved(event);

        // then
        verify(requestService).cancelPendingRequestsForItemRemoved(100L);
    }
}
