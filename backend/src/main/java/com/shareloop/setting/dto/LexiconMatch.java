package com.shareloop.setting.dto;

/** A lexicon rule that matched text, including whether diacritic normalization was required. */
public record LexiconMatch(String keyword, String level, String group, MatchedAs matchedAs) {

    public enum MatchedAs {
        EXACT,
        NORMALIZED
    }
}
