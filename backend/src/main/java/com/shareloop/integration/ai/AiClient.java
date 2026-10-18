package com.shareloop.integration.ai;

import java.time.Duration;

public interface AiClient {
    default boolean isAvailable() {
        return true;
    }

    AiResponse screen(AiScreeningContent content, Duration timeout) throws Exception;
}
