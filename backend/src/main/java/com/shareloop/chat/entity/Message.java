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
 * Thực thể tin nhắn trong khung chat (bảng messages).
 */
@Entity
@Table(name = "messages")
public class Message {

    // Giá trị cột offer_status (CHECK trong V1__init.sql), chỉ dùng cho tin OFFER_CHANGE.
    public static final String OFFER_PENDING = "PENDING";
    public static final String OFFER_ACCEPTED = "ACCEPTED";
    public static final String OFFER_DECLINED = "DECLINED";

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
        this(requestId, senderId, kind, content, mediaId, null);
    }

    public Message(Long requestId, Long senderId, MessageKind kind, String content, Long mediaId, Long refItemId) {
        this.requestId = requestId;
        this.senderId = senderId;
        this.kind = kind != null ? kind : MessageKind.TEXT;
        this.content = content;
        this.mediaId = mediaId;
        this.refItemId = refItemId;
        this.moderation = MessageModeration.PASS;
        this.createdAt = Instant.now();
    }

    /**
     * Tạo tin đề nghị đưa món của người nhận vào đổi (Cho → Trao đổi), ở trạng thái PENDING chờ chủ bài trả lời.
     *
     * @param offeredItemId id món người nhận đề nghị đem đổi, lưu ở ref_item_id để ghép thẻ món như ITEM_CARD
     * @param content lời nhắn kèm theo, có thể null
     */
    public static Message offerChange(Long requestId, Long receiverId, Long offeredItemId, String content) {
        Message message = new Message(requestId, receiverId, MessageKind.OFFER_CHANGE, content, null, offeredItemId);
        message.offerStatus = OFFER_PENDING;
        return message;
    }

    /** Tin hệ thống trong khung chat, không có người gửi. */
    public static Message system(Long requestId, String content) {
        return new Message(requestId, null, MessageKind.SYSTEM, content);
    }

    /** Đề nghị đổi món đang chờ chủ bài trả lời. */
    public boolean isPendingOfferChange() {
        return kind == MessageKind.OFFER_CHANGE && OFFER_PENDING.equals(offerStatus);
    }

    /** Chủ bài đồng ý đề nghị đổi món. Chỉ đề nghị đang PENDING mới được trả lời. */
    public void accept() {
        requirePendingOfferChange();
        this.offerStatus = OFFER_ACCEPTED;
    }

    /** Chủ bài từ chối, hoặc hệ thống tự huỷ đề nghị đổi món. Chỉ đề nghị đang PENDING mới được trả lời. */
    public void decline() {
        requirePendingOfferChange();
        this.offerStatus = OFFER_DECLINED;
    }

    private void requirePendingOfferChange() {
        if (!isPendingOfferChange()) {
            throw new IllegalStateException("Message " + id + " is not a pending offer change");
        }
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

    public void setAiConfidence(BigDecimal confidence) {
        this.aiConfidence = confidence;
        this.moderationTier = 2;
    }
}
