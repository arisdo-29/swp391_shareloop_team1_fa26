package com.shareloop.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Thực thể tin nhắn trong khung chat (ERD mục 7.4, bảng messages).
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "sender_id")
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 12)
    private MessageKind kind = MessageKind.TEXT;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "media_id")
    private Long mediaId;

    @Column(name = "ref_item_id")
    private Long refItemId;

    @Column(name = "offer_status", length = 10)
    private String offerStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation", nullable = false, length = 8)
    private MessageModeration moderation = MessageModeration.PASS;

    @Column(name = "moderation_tier")
    private Short moderationTier;

    @Column(name = "blocked_reason", length = 100)
    private String blockedReason;

    @Column(name = "ai_confidence", precision = 3, scale = 2)
    private BigDecimal aiConfidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Message() {}

    public Message(Long requestId, Long senderId, String content) {
        this.requestId = requestId;
        this.senderId = senderId;
        this.kind = MessageKind.TEXT;
        this.content = content;
        this.moderation = MessageModeration.PASS;
        this.createdAt = Instant.now();
    }

    public Message(Long requestId, Long senderId, MessageKind kind, String content) {
        this(requestId, senderId, kind, content, null);
    }

    public Message(Long requestId, Long senderId, MessageKind kind, String content, Long mediaId) {
        this.requestId = requestId;
        this.senderId = senderId;
        this.kind = kind != null ? kind : MessageKind.TEXT;
        this.content = content;
        this.mediaId = mediaId;
        this.moderation = MessageModeration.PASS;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public MessageKind getKind() {
        return kind;
    }

    public String getContent() {
        return content;
    }

    public Long getMediaId() {
        return mediaId;
    }

    public Long getRefItemId() {
        return refItemId;
    }

    public String getOfferStatus() {
        return offerStatus;
    }

    public MessageModeration getModeration() {
        return moderation;
    }

    public Short getModerationTier() {
        return moderationTier;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public BigDecimal getAiConfidence() {
        return aiConfidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void block(int tier, String reason) {
        this.moderation = MessageModeration.BLOCKED;
        this.moderationTier = (short) tier;
        this.blockedReason = reason;
    }
}
