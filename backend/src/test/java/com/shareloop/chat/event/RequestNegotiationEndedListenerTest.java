package com.shareloop.chat.event;

import static org.mockito.Mockito.verify;

import com.shareloop.chat.service.ChatService;
import com.shareloop.request.event.RequestNegotiationEndedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestNegotiationEndedListenerTest {

    @Mock
    ChatService chatService;

    @InjectMocks
    RequestNegotiationEndedListener listener;

    @Test
    void onNegotiationEnded_callsDeclinePendingOfferChanges() {
        // given
        RequestNegotiationEndedEvent event = new RequestNegotiationEndedEvent(100L);

        // when
        listener.onNegotiationEnded(event);

        // then
        verify(chatService).declinePendingOfferChanges(100L);
    }
}
