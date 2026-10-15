package com.shareloop.setting.dto;

import jakarta.validation.constraints.NotNull;

public record AdminLexiconActiveRequest(@NotNull Boolean active) {}
