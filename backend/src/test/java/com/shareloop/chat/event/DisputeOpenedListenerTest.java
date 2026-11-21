package com.shareloop.chat.event;

import static org.mockito.Mockito.verify;

import com.shareloop.chat.service.ChatService;
import com.shareloop.report.event.DisputeOpenedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisputeOpenedListenerTest {

    @Mock
    ChatService chatService;

    @InjectMocks
    DisputeOpenedListener listener;

    @Test
    void onDisputeOpened_postsSystemNoticeWithResponseHours() {
        // given
        DisputeOpenedEvent event = new DisputeOpenedEvent(100L, 48);

        // when
        listener.onDisputeOpened(event);

        // then
        verify(chatService).postDisputeOpenedNotice(100L, 48);
    }
}
