package com.shareloop.media.entity;

import com.shareloop.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "media_files")
@SQLRestriction("is_deleted = false")
public class MediaFile extends BaseEntity {
    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "visibility", nullable = false, length = 10)
    private String visibility = "PUBLIC";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private MediaStatus status = MediaStatus.UPLOADED;

    protected MediaFile() {}

    public MediaFile(String storageKey, String mimeType, long sizeBytes) {
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getVisibility() {
        return visibility;
    }

    public MediaStatus getStatus() {
        return status;
    }

    public void attach() {
        if (status != MediaStatus.UPLOADED) {
            throw new IllegalStateException("Media is not uploaded");
        }
        status = MediaStatus.ATTACHED;
    }

    public void makePrivate() {
        visibility = "PRIVATE";
    }

    public void detach() {
        if (status != MediaStatus.ATTACHED) {
            throw new IllegalStateException("Media is not attached");
        }
        status = MediaStatus.UPLOADED;
    }
}
