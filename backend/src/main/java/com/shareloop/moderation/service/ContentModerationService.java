package com.shareloop.moderation.service;

import com.shareloop.catalog.dto.CategoryResponse;
import com.shareloop.catalog.service.CategoryService;
import com.shareloop.moderation.ContactInfoDetector;
import com.shareloop.moderation.ModerationResult;
import com.shareloop.setting.dto.LexiconEntry;
import com.shareloop.setting.dto.LexiconMatch;
import com.shareloop.setting.dto.LexiconMatch.MatchedAs;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.setting.service.LexiconMatcher;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Deterministic tier-one moderation for newly submitted posts. */
@Service
public class ContentModerationService {

    private final CategoryService categoryService;
    private final ConfigService configService;
    private final LexiconMatcher lexiconMatcher;

    public ContentModerationService(
            CategoryService categoryService, ConfigService configService, LexiconMatcher lexiconMatcher) {
        this.categoryService = categoryService;
        this.configService = configService;
        this.lexiconMatcher = lexiconMatcher;
    }

    /**
     * Checks restriction, contact details, then the active lexicon. Any unexpected failure returns REVIEW so a
     * technical error never silently passes content.
     */
    public ModerationResult checkPost(Long categoryId, String title, String description) {
        if (categoryId == null || categoryId <= 0) {
            return ModerationResult.review("INVALID_INPUT", List.of());
        }

        try {
            CategoryResponse category = categoryService.getById(categoryId);
            if (category.restricted()) {
                return ModerationResult.block("RESTRICTED_CATEGORY", List.of(category.name()));
            }

            if (title == null || title.isBlank() || description == null || description.isBlank()) {
                return ModerationResult.review("INVALID_INPUT", List.of());
            }

            List<String> content = List.of(title, description);
            List<String> contactMatches = new ArrayList<>();
            for (String text : content) {
                ContactInfoDetector.detect(text)
                        .forEach(match -> contactMatches.add(match.type().name()));
            }
            if (!contactMatches.isEmpty()) {
                return ModerationResult.block("CONTACT_INFO", contactMatches);
            }

            List<LexiconEntry> lexicon = configService.lexicon();
            return checkLexicon(content, lexicon);
        } catch (RuntimeException exception) {
            return ModerationResult.review("MODERATION_CHECK_FAILED", List.of());
        }
    }

    private ModerationResult checkLexicon(List<String> content, List<LexiconEntry> lexicon) {
        List<LexiconEntry> applicable = new ArrayList<>();
        for (LexiconEntry entry : lexicon) {
            if (entry == null || entry.term() == null || entry.term().isBlank() || entry.scope() == null) {
                return ModerationResult.review("INVALID_LEXICON_RULE", List.of());
            }
            if (entry.scope().equalsIgnoreCase("CHAT") || entry.scope().equalsIgnoreCase("MESSAGE")) {
                continue;
            }
            if (!entry.scope().equalsIgnoreCase("POST") && !entry.scope().equalsIgnoreCase("BOTH")) {
                return ModerationResult.review("INVALID_LEXICON_RULE", List.of(entry.term()));
            }
            applicable.add(entry);
        }
        List<LexiconMatch> matches = content.stream()
                .flatMap(text -> lexiconMatcher.match(text, applicable).stream())
                .toList();
        List<String> blockTerms = matches.stream()
                .filter(match -> match.matchedAs() == MatchedAs.EXACT)
                .filter(match -> "BLOCK".equalsIgnoreCase(match.level()))
                .map(LexiconMatch::keyword)
                .distinct()
                .toList();
        if (!blockTerms.isEmpty()) {
            return ModerationResult.block("LEXICON_BLOCK", blockTerms);
        }
        List<String> reviewTerms =
                matches.stream().map(LexiconMatch::keyword).distinct().toList();
        return reviewTerms.isEmpty() ? ModerationResult.pass() : ModerationResult.review("LEXICON_REVIEW", reviewTerms);
    }
}
