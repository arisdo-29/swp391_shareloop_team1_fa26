package com.shareloop.setting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shareloop.setting.dto.LexiconEntry;
import com.shareloop.setting.dto.LexiconMatch.MatchedAs;
import java.util.List;
import org.junit.jupiter.api.Test;

class LexiconMatcherTest {

    private final LexiconMatcher matcher = new LexiconMatcher();

    @Test
    void exactBlockMatchKeepsItsLevelAndGroup() {
        var result = matcher.match("BÁN kính", List.of(rule("bán", "BLOCK", List.of(), "WEAPONS")));

        assertThat(result).singleElement().satisfies(match -> {
            assertThat(match.keyword()).isEqualTo("bán");
            assertThat(match.level()).isEqualTo("BLOCK");
            assertThat(match.group()).isEqualTo("WEAPONS");
            assertThat(match.matchedAs()).isEqualTo(MatchedAs.EXACT);
        });
    }

    @Test
    void normalizesVietnameseDiacriticsAndDowngradesBlockToReview() {
        var result = matcher.match("ban kinh", List.of(rule("bán", "BLOCK", List.of(), "WEAPONS")));

        assertThat(result).singleElement().satisfies(match -> {
            assertThat(match.level()).isEqualTo("REVIEW");
            assertThat(match.matchedAs()).isEqualTo(MatchedAs.NORMALIZED);
        });
    }

    @Test
    void exactReviewMatchKeepsReviewLevel() {
        var result = matcher.match("đồ cũ", List.of(rule("đồ cũ", "REVIEW", List.of(), "GOODS")));

        assertThat(result).singleElement().satisfies(match -> {
            assertThat(match.level()).isEqualTo("REVIEW");
            assertThat(match.matchedAs()).isEqualTo(MatchedAs.EXACT);
        });
    }

    @Test
    void normalizesVietnameseDStroke() {
        var result = matcher.match("do cu", List.of(rule("đồ cũ", "BLOCK", List.of(), "GOODS")));

        assertThat(result).singleElement().satisfies(match -> {
            assertThat(match.level()).isEqualTo("REVIEW");
            assertThat(match.matchedAs()).isEqualTo(MatchedAs.NORMALIZED);
        });
    }

    @Test
    void exceptionPhraseExcludesItsKeywordAndMatchingIsCaseAndWhitespaceInsensitive() {
        var result = matcher.match("  BÁN    kính ", List.of(rule("bán", "BLOCK", List.of("bán kính"), "WEAPONS")));
        var caseResult = matcher.match("  BÁN   lẻ ", List.of(rule("bán lẻ", "REVIEW", List.of(), "WEAPONS")));

        assertThat(result).isEmpty();
        assertThat(caseResult).singleElement().satisfies(match -> {
            assertThat(match.level()).isEqualTo("REVIEW");
            assertThat(match.matchedAs()).isEqualTo(MatchedAs.EXACT);
        });
    }

    @Test
    void wordModeDoesNotMatchInsideAnInnocentWord() {
        assertThat(matcher.match("Scunthorpe", List.of(rule("cunt", "BLOCK", List.of(), "TEST"))))
                .isEmpty();
    }

    @Test
    void regexKeepsUppercaseEscapesInsteadOfLowercasingThem() {
        // \D là "không phải chữ số"; trước đây bị viết thường thành \d nên khớp ngược.
        var rule = List.of(regexRule("a\\Db"));

        assertThat(matcher.match("a-b", rule))
                .singleElement()
                .satisfies(match -> assertThat(match.matchedAs()).isEqualTo(MatchedAs.EXACT));
        assertThat(matcher.match("a1b", rule)).isEmpty();
    }

    @Test
    void regexIsStillCaseInsensitive() {
        assertThat(matcher.match("ZALO 0912", List.of(regexRule("zalo\\s*\\d+"))))
                .hasSize(1);
    }

    @Test
    void validateRuleRejectsRegexThatDoesNotCompile() {
        assertThatThrownBy(() -> LexiconMatcher.validateRule("[abc", "REGEX"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> LexiconMatcher.validateRule("0\\d{9}", "REGEX")).doesNotThrowAnyException();
        assertThatCode(() -> LexiconMatcher.validateRule("[abc", "WORD")).doesNotThrowAnyException();
    }

    private static LexiconEntry rule(String term, String level, List<String> exceptions, String group) {
        return new LexiconEntry(term, group, level, "WORD", exceptions, "POST", 1);
    }

    private static LexiconEntry regexRule(String pattern) {
        return new LexiconEntry(pattern, "CONTACT", "BLOCK", "REGEX", List.of(), "POST", 1);
    }
}
