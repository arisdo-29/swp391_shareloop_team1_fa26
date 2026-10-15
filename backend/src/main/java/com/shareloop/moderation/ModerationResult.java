package com.shareloop.moderation;

import java.util.List;

/** Internal result of tier-one post moderation. */
public record ModerationResult(ModerationStatus status, String reasonCode, List<String> matchedTerms) {

    public ModerationResult {
        matchedTerms = List.copyOf(matchedTerms);
    }

    public static ModerationResult pass() {
        return new ModerationResult(ModerationStatus.PASS, "PASS", List.of());
    }

    public static ModerationResult block(String reasonCode, List<String> matchedTerms) {
        return new ModerationResult(ModerationStatus.BLOCK, reasonCode, matchedTerms);
    }

    public static ModerationResult review(String reasonCode, List<String> matchedTerms) {
        return new ModerationResult(ModerationStatus.REVIEW, reasonCode, matchedTerms);
    }
}
