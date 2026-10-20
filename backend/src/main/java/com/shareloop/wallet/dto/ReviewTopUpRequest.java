package com.shareloop.wallet.dto;

import jakarta.validation.constraints.Size;

public record ReviewTopUpRequest(@Size(max = 500) String note) {}
