package com.shareloop.integration.ai;

import java.time.Duration;

/** Reads a user's single search request against the supplied public item catalog. */
public interface AiSearchClient {
    boolean isAvailable();

    String model();

    AiResponse recommend(String query, String catalog, Duration timeout) throws Exception;
}
