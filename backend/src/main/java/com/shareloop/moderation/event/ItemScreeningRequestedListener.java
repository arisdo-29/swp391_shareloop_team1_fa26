package com.shareloop.moderation.event;

import com.shareloop.moderation.service.ItemScreeningService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ItemScreeningRequestedListener {
    private final ItemScreeningService screeningService;
    private final ConfigService configService;

    public ItemScreeningRequestedListener(ItemScreeningService screeningService, ConfigService configService) {
        this.screeningService = screeningService;
        this.configService = configService;
    }

    @Async("itemScreeningExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmission(ItemScreeningRequestedEvent event) {
        // SRS v11 bỏ tầng AI gắn cờ: tắt cờ thì không gọi LLM, bài vào thẳng hàng chờ duyệt.
        if (!configService.getBoolean(ConfigKey.ITEM_AI_SCREENING_ENABLED)) {
            return;
        }
        screeningService.screen(event);
    }
}
