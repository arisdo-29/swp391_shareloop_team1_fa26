package com.shareloop.integration.ai;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public interface ChatAiClient {
    boolean isAvailable();

    AiResponse checkContact(String message, List<String> context, Duration timeout)
            throws IOException, InterruptedException;
}
