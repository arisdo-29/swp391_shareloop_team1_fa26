package com.shareloop.wallet.dto;

import java.time.Instant;

public record UnlinkedTopUp(long id, long userId, int amount, Instant createdAt) {}
