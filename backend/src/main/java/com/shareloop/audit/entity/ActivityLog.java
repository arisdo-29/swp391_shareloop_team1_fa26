package com.shareloop.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "activity_logs")
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(name = "target_type", length = 20)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> data;

    @Column(columnDefinition = "text")
    private String reason;

    protected ActivityLog() {}

    public ActivityLog(
            Long actorId, String type, String targetType, Long targetId, Map<String, Object> data, String reason) {
        this.actorId = actorId;
        this.type = type;
        this.targetType = targetType;
        this.targetId = targetId;
        this.data = data;
        this.reason = reason;
    }
}
