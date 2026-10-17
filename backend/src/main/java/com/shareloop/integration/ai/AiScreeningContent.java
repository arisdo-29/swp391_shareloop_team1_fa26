package com.shareloop.integration.ai;

/** Only post content is sent to the AI provider. */
public record AiScreeningContent(
        String title, String description, String desiredItem, String category, String condition, String offerType) {}
