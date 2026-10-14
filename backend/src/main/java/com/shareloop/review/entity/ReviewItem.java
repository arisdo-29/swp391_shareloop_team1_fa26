package com.shareloop.review.entity;

import com.shareloop.common.entity.SliceAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/** Review-owned columns from items; item status remains owned by ItemService. */
@Entity
@Table(name = "items")
@SQLRestriction("is_deleted = false")
public class ReviewItem extends SliceAudit {

    @Id
    private Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> reviewSnapshot;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    protected ReviewItem() {}

    public Long getId() {
        return id;
    }

    public void approve(Map<String, Object> snapshot, Instant approvedAt) {
        this.reviewSnapshot = Map.copyOf(snapshot);
        this.approvedAt = approvedAt;
        this.rejectReason = null;
    }

    public void reject(String reason) {
        this.rejectReason = reason;
    }
}
