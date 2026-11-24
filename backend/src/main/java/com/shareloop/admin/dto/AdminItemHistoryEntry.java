package com.shareloop.admin.dto;

import java.time.Instant;

public record AdminItemHistoryEntry(
        String key, String action, ActorRole actorRole, String actorName, Instant createdAt, String reason) {

    public enum ActorRole {
        ADMIN,
        OWNER,
        USER,
        SYSTEM
    }
}
