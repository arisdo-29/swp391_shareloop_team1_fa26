package com.shareloop.integration.ai;

import java.time.Duration;

/** Extracts search filters only; item data is always read from ShareLoop's database. */
public interface AiSearchClient {
    boolean isAvailable();

    String model();

    AiResponse extract(String query, String allowedFilters, Duration timeout) throws Exception;
}
