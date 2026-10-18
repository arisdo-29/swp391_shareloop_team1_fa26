package com.shareloop.request.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Entity ánh xạ bảng request_media_mappings (ERD 7.5; FR-86, BR-T09).
 * Quản lý ảnh/video minh chứng đính kèm khi giao nhận hoặc khiếu nại.
 */
@Entity
@Table(name = "request_media_mappings")
@IdClass(RequestMediaMapping.RequestMediaId.class)
public class RequestMediaMapping {

    @Id
    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Id
    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    @Column(name = "stage", nullable = false, length = 10)
    private String stage; // 'HANDOVER' hoặc 'DISPUTE'

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RequestMediaMapping() {}

    public RequestMediaMapping(Long requestId, Long mediaId, String stage, Long uploadedBy) {
        this.requestId = requestId;
        this.mediaId = mediaId;
        this.stage = stage;
        this.uploadedBy = uploadedBy;
        this.createdAt = Instant.now();
    }

    public Long getRequestId() {
        return requestId;
    }

    public Long getMediaId() {
        return mediaId;
    }

    public String getStage() {
        return stage;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static class RequestMediaId implements Serializable {
        private Long requestId;
        private Long mediaId;

        public RequestMediaId() {}

        public RequestMediaId(Long requestId, Long mediaId) {
            this.requestId = requestId;
            this.mediaId = mediaId;
        }

        public Long getRequestId() {
            return requestId;
        }

        public Long getMediaId() {
            return mediaId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RequestMediaId that = (RequestMediaId) o;
            return Objects.equals(requestId, that.requestId) && Objects.equals(mediaId, that.mediaId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestId, mediaId);
        }
    }
}
