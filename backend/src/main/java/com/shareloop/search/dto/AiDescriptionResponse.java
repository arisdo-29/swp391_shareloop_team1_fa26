package com.shareloop.search.dto;

import java.util.List;

public record AiDescriptionResponse(
        String description, List<AttributeSuggestion> attributes, int remainingUses, String message) {
    public record AttributeSuggestion(String name, String value, boolean requiresUserInput) {}
}
