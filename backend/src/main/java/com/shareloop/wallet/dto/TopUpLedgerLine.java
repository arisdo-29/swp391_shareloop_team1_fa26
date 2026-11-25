package com.shareloop.wallet.dto;

import java.time.Instant;

/** Dòng sổ cái đã cộng Credit cho một đơn nạp. */
public record TopUpLedgerLine(long id, int amount, long balanceAfter, Instant createdAt) {}
