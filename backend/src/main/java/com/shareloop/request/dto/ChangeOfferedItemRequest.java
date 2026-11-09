package com.shareloop.request.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeOfferedItemRequest(@NotNull Long offeredItemId) {}
