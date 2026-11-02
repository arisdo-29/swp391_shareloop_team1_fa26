package com.shareloop.integration.ai;

import java.time.Duration;

/** Reads a user's single search request against the supplied public item catalog. */
public interface AiSearchClient {
    boolean isAvailable();

    String model();

    AiResponse extract(String query, String allowedFilters, Duration timeout) throws Exception;

    default AiResponse recommend(String query, String catalog, Duration timeout) throws Exception {
        return extract(query, catalog, timeout);
    }
}
