package com.shareloop.chat.event;

import com.shareloop.chat.service.ChatService;
import com.shareloop.request.event.ScheduleSavedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ScheduleSavedListener {
    private final ChatService chatService;

    public ScheduleSavedListener(ChatService chatService) {
        this.chatService = chatService;
    }

    @EventListener
    public void onScheduleSaved(ScheduleSavedEvent event) {
        chatService.postScheduleSavedNotice(event.requestId(), event.actorId());
    }
}
