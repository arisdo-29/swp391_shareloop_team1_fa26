package com.shareloop.setting.dto;

import java.util.List;

public record AdminLexiconEntry(
        Long id,
        String keyword,
        String group,
        String level,
        String matchMode,
        List<String> exceptions,
        String scope,
        int sortOrder,
        boolean active) {}
