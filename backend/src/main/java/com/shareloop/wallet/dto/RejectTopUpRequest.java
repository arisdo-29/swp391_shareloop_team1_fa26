package com.shareloop.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectTopUpRequest(@NotBlank @Size(max = 500) String reason) {}
