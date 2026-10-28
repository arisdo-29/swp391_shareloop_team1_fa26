package com.shareloop.moderation.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shareloop.moderation.service.ItemScreeningService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemScreeningRequestedListenerTest {

    private final ItemScreeningService screeningService = mock(ItemScreeningService.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final ItemScreeningRequestedListener listener =
            new ItemScreeningRequestedListener(screeningService, configService);

    private final ItemScreeningRequestedEvent event =
            new ItemScreeningRequestedEvent(8L, 9L, 3L, "GIVE", "Áo khoác", "Còn mới", null, "LIKE_NEW");

    @Test
    @DisplayName("SRS v11: tắt AI sàng lọc thì đăng bài không gọi LLM")
    void disabled_doesNotScreen() {
        when(configService.getBoolean(ConfigKey.ITEM_AI_SCREENING_ENABLED)).thenReturn(false);

        listener.onSubmission(event);

        verifyNoInteractions(screeningService);
    }

    @Test
    @DisplayName("Admin bật lại cấu hình thì vẫn sàng lọc như trước")
    void enabled_screensTheItem() {
        when(configService.getBoolean(ConfigKey.ITEM_AI_SCREENING_ENABLED)).thenReturn(true);

        listener.onSubmission(event);

        verify(screeningService).screen(event);
    }
}
