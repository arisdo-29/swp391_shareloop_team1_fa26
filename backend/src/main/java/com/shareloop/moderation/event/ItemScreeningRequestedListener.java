package com.shareloop.moderation.event;

import com.shareloop.moderation.service.ItemScreeningService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ItemScreeningRequestedListener {
    private final ItemScreeningService screeningService;

    public ItemScreeningRequestedListener(ItemScreeningService screeningService) {
        this.screeningService = screeningService;
    }

    @Async("itemScreeningExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmission(ItemScreeningRequestedEvent event) {
        screeningService.screen(event);
    }
}
