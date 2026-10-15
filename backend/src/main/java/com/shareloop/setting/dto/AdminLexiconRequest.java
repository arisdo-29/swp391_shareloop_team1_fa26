package com.shareloop.setting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminLexiconRequest(
        @NotBlank(message = "Keyword không được để trống.") String keyword,

        @NotBlank(message = "Group không được để trống.") @Size(max = 80)
        String group,

        @NotBlank(message = "Level không được để trống.") String level,
        String matchMode,
        List<@NotBlank(message = "Exception không được để trống.") String> exceptions,
        @NotBlank(message = "Scope không được để trống.") String scope) {}
