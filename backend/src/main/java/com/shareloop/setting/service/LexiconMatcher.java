package com.shareloop.setting.service;

import com.shareloop.setting.dto.LexiconEntry;
import com.shareloop.setting.dto.LexiconMatch;
import com.shareloop.setting.dto.LexiconMatch.MatchedAs;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Pure lexicon matching rules shared by moderation and the admin quick-test endpoint. */
@Component
public class LexiconMatcher {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String WORD_START = "(?<![\\p{L}\\p{N}_])";
    private static final String WORD_END = "(?![\\p{L}\\p{N}_])";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    public List<LexiconMatch> match(String text, List<LexiconEntry> entries) {
        if (text == null || text.isBlank() || entries == null || entries.isEmpty()) {
            return List.of();
        }

        List<LexiconMatch> matches = new ArrayList<>();
        for (LexiconEntry entry : entries) {
            if (entry == null || entry.term() == null || entry.term().isBlank()) {
                throw new IllegalArgumentException("Lexicon contains an empty keyword");
            }

            Pattern exactRule = compileRule(entry.term(), entry.matchMode(), false);
            Pattern normalizedRule = compileRule(entry.term(), entry.matchMode(), true);
            List<Pattern> exactExceptions = compileExceptions(entry.exceptions(), false);
            List<Pattern> normalizedExceptions = compileExceptions(entry.exceptions(), true);

            boolean exact = hasNonExceptionMatch(text, exactRule, exactExceptions, false);
            boolean normalized = !exact && hasNonExceptionMatch(text, normalizedRule, normalizedExceptions, true);
            if (exact) {
                matches.add(new LexiconMatch(entry.term(), entry.severity(), entry.category(), MatchedAs.EXACT));
            } else if (normalized) {
                matches.add(new LexiconMatch(entry.term(), "REVIEW", entry.category(), MatchedAs.NORMALIZED));
            }
        }
        return List.copyOf(matches);
    }

    private static Pattern compileRule(String term, String matchMode, boolean removeDiacritics) {
        if (matchMode == null) {
            throw new IllegalArgumentException("Lexicon match mode is missing");
        }
        String normalizedTerm = normalize(term, removeDiacritics);
        if (normalizedTerm.isBlank()) {
            throw new IllegalArgumentException("Lexicon keyword is blank after normalization");
        }

        String expression =
                switch (matchMode.toUpperCase(Locale.ROOT)) {
                    case "WORD", "PHRASE" -> WORD_START + flexibleWhitespace(Pattern.quote(normalizedTerm)) + WORD_END;
                    case "REGEX" -> normalizedTerm;
                    default -> throw new IllegalArgumentException("Unsupported lexicon match mode");
                };
        return Pattern.compile(expression, FLAGS);
    }

    private static List<Pattern> compileExceptions(List<String> exceptions, boolean removeDiacritics) {
        if (exceptions == null) {
            return List.of();
        }
        return exceptions.stream()
                .filter(exception -> exception != null && !exception.isBlank())
                .map(exception -> normalize(exception, removeDiacritics))
                .map(Pattern::quote)
                .map(LexiconMatcher::flexibleWhitespace)
                .map(expression -> Pattern.compile(WORD_START + expression + WORD_END, FLAGS))
                .toList();
    }

    private static boolean hasNonExceptionMatch(
            String text, Pattern rule, List<Pattern> exceptions, boolean normalize) {
        String candidate = normalize(text, normalize);
        Matcher matcher = rule.matcher(candidate);
        while (matcher.find()) {
            boolean excluded = exceptions.stream().anyMatch(exception -> encloses(exception, candidate, matcher));
            if (!excluded) {
                return true;
            }
        }
        return false;
    }

    private static boolean encloses(Pattern exception, String text, Matcher ruleMatch) {
        Matcher matcher = exception.matcher(text);
        while (matcher.find()) {
            if (matcher.start() <= ruleMatch.start() && matcher.end() >= ruleMatch.end()) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value, boolean removeDiacritics) {
        String normalized = Normalizer.normalize(value, removeDiacritics ? Normalizer.Form.NFD : Normalizer.Form.NFC);
        if (removeDiacritics) {
            normalized = normalized.replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D');
        }
        return WHITESPACE
                .matcher(normalized.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim();
    }

    private static String flexibleWhitespace(String quoted) {
        return quoted.replace("\\Q \\E", "\\E\\s+\\Q");
    }
}
