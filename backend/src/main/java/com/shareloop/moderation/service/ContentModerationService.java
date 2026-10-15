package com.shareloop.moderation.service;

import com.shareloop.catalog.dto.CategoryResponse;
import com.shareloop.catalog.service.CategoryService;
import com.shareloop.moderation.ContactInfoDetector;
import com.shareloop.moderation.ModerationResult;
import com.shareloop.setting.dto.LexiconEntry;
import com.shareloop.setting.service.ConfigService;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Deterministic tier-one moderation for newly submitted posts. */
@Service
public class ContentModerationService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String WORD_BOUNDARY_START = "(?<![\\p{L}\\p{N}_])";
    private static final String WORD_BOUNDARY_END = "(?![\\p{L}\\p{N}_])";

    private final CategoryService categoryService;
    private final ConfigService configService;

    public ContentModerationService(CategoryService categoryService, ConfigService configService) {
        this.categoryService = categoryService;
        this.configService = configService;
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
        List<String> reviewTerms = new ArrayList<>();

        for (LexiconEntry entry : lexicon) {
            if (entry == null || entry.term() == null || entry.term().isBlank()) {
                return ModerationResult.review("INVALID_LEXICON_RULE", List.of());
            }
            if (entry.scope() == null) {
                return ModerationResult.review("INVALID_LEXICON_RULE", List.of(entry.term()));
            }
            if (entry.scope().equalsIgnoreCase("CHAT") || entry.scope().equalsIgnoreCase("MESSAGE")) {
                continue;
            }
            if (!entry.scope().equalsIgnoreCase("POST") && !entry.scope().equalsIgnoreCase("BOTH")) {
                return ModerationResult.review("INVALID_LEXICON_RULE", List.of(entry.term()));
            }

            Pattern originalPattern = compileRule(entry.term(), entry.matchMode(), false);
            Pattern normalizedPattern = compileRule(entry.term(), entry.matchMode(), true);
            List<Pattern> originalExceptions = compileExceptions(entry.exceptions(), false);
            List<Pattern> normalizedExceptions = compileExceptions(entry.exceptions(), true);
            boolean matchedOriginal = false;
            boolean matchedWithoutDiacritics = false;

            for (String text : content) {
                matchedOriginal |= hasNonExceptionMatch(text, originalPattern, originalExceptions, false);
                matchedWithoutDiacritics |= hasNonExceptionMatch(text, normalizedPattern, normalizedExceptions, true);
            }

            if (!matchedOriginal && !matchedWithoutDiacritics) {
                continue;
            }

            if (!matchedOriginal) {
                reviewTerms.add(entry.term());
                continue;
            }

            if (entry.severity() == null) {
                return ModerationResult.review("INVALID_LEXICON_RULE", List.of(entry.term()));
            }
            if (entry.severity().equalsIgnoreCase("BLOCK")) {
                return ModerationResult.block("LEXICON_BLOCK", List.of(entry.term()));
            }
            if (entry.severity().equalsIgnoreCase("REVIEW")) {
                reviewTerms.add(entry.term());
            } else {
                return ModerationResult.review("INVALID_LEXICON_RULE", List.of(entry.term()));
            }
        }

        return reviewTerms.isEmpty() ? ModerationResult.pass() : ModerationResult.review("LEXICON_REVIEW", reviewTerms);
    }

    private static Pattern compileRule(String term, String matchMode, boolean removeDiacritics) {
        if (matchMode == null) {
            throw new IllegalArgumentException("Lexicon match mode is missing");
        }

        String normalizedTerm = normalize(term, removeDiacritics);
        if (normalizedTerm.isBlank()) {
            throw new IllegalArgumentException("Lexicon term is blank after normalization");
        }
        String expression =
                switch (matchMode.toUpperCase(Locale.ROOT)) {
                    case "WORD", "PHRASE" ->
                        WORD_BOUNDARY_START + flexibleWhitespace(Pattern.quote(normalizedTerm)) + WORD_BOUNDARY_END;
                    case "REGEX" -> normalizedTerm;
                    default -> throw new IllegalArgumentException("Unsupported lexicon match mode");
                };
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static List<Pattern> compileExceptions(List<String> exceptions, boolean removeDiacritics) {
        if (exceptions == null) {
            return List.of();
        }
        return exceptions.stream()
                .filter(exception -> exception != null && !exception.isBlank())
                .map(exception -> normalize(exception, removeDiacritics))
                .map(Pattern::quote)
                .map(ContentModerationService::flexibleWhitespace)
                .map(expression -> Pattern.compile(
                        WORD_BOUNDARY_START + expression + WORD_BOUNDARY_END,
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
                .toList();
    }

    private static boolean hasNonExceptionMatch(
            String content, Pattern rule, List<Pattern> exceptions, boolean removeDiacritics) {
        String normalizedContent = normalize(content, removeDiacritics);
        Matcher ruleMatcher = rule.matcher(normalizedContent);
        while (ruleMatcher.find()) {
            boolean excluded =
                    exceptions.stream().anyMatch(exception -> encloses(exception, normalizedContent, ruleMatcher));
            if (!excluded) {
                return true;
            }
        }
        return false;
    }

    private static boolean encloses(Pattern exception, String text, Matcher ruleMatch) {
        Matcher exceptionMatcher = exception.matcher(text);
        while (exceptionMatcher.find()) {
            if (exceptionMatcher.start() <= ruleMatch.start() && exceptionMatcher.end() >= ruleMatch.end()) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text, boolean removeDiacritics) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        if (removeDiacritics) {
            normalized = ContactInfoDetector.normalize(normalized);
        }
        return WHITESPACE.matcher(normalized).replaceAll(" ").trim();
    }

    private static String flexibleWhitespace(String quoted) {
        return quoted.replace("\\Q \\E", "\\E\\s+\\Q");
    }
}
