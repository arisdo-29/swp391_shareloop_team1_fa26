package com.shareloop.setting.dto;

public record AdminChecklistEntry(Long id, String key, String question, int sortOrder, boolean active) {}
