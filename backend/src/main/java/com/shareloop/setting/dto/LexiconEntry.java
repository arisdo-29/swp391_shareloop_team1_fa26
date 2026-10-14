package com.shareloop.setting.dto;

import java.util.List;

/** An active lexicon rule configured in website_attributes. */
public record LexiconEntry(
        String term,
        String category,
        String severity,
        String matchMode,
        List<String> exceptions,
        String scope,
        int sortOrder) {}
