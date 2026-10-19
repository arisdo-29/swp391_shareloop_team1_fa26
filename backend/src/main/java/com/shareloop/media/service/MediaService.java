package com.shareloop.media.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.storage.StorageService;
import com.shareloop.media.MediaErrorCode;
import com.shareloop.media.dto.MediaResponse;
import com.shareloop.media.entity.MediaFile;
import com.shareloop.media.entity.MediaStatus;
import com.shareloop.media.repository.MediaFileRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final MediaFileRepository repository;
    private final StorageService storage;
    private final ConfigService config;

    public MediaService(MediaFileRepository repository, StorageService storage, ConfigService config) {
        this.repository = repository;
        this.storage = storage;
        this.config = config;
    }

    @Transactional
    public MediaResponse upload(MultipartFile file, long userId) {
        long maxBytes = config.getInt(ConfigKey.IMAGE_MAX_MB) * 1024L * 1024L;
        if (file.isEmpty()) {
            throw new BusinessException(MediaErrorCode.MEDIA_INVALID_FILE);
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException(MediaErrorCode.MEDIA_FILE_TOO_LARGE);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(MediaErrorCode.MEDIA_STORAGE_ERROR);
        }
        if (bytes.length > maxBytes) {
            throw new BusinessException(MediaErrorCode.MEDIA_FILE_TOO_LARGE);
        }
        ImageFormat format = ImageFormat.detect(bytes);
        if (format == null) {
            throw new BusinessException(MediaErrorCode.MEDIA_INVALID_FILE);
        }

        String key = null;
        try {
            key = storage.save(bytes, format.extension());
            MediaFile media = repository.saveAndFlush(new MediaFile(key, format.mimeType(), bytes.length));
            if (media.getCreatedBy() == null || media.getCreatedBy() != userId) {
                throw new IllegalStateException("Media owner audit is missing");
            }
            return new MediaResponse(media.getId(), "/api/v1/media/" + media.getId());
        } catch (IOException | RuntimeException exception) {
            if (key != null) {
                try {
                    storage.delete(key);
                } catch (IOException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw new BusinessException(MediaErrorCode.MEDIA_STORAGE_ERROR);
        }
    }

    @Transactional
    public void attach(List<Long> mediaIds, long userId) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return;
        }
        if (mediaIds.stream().anyMatch(Objects::isNull) || new HashSet<>(mediaIds).size() != mediaIds.size()) {
            throw new BusinessException(MediaErrorCode.MEDIA_INVALID_FILE);
        }
        List<MediaFile> files =
                mediaIds.stream().sorted().map(this::requireMediaForUpdate).toList();
        for (MediaFile file : files) {
            requireOwner(file, userId);
            if (!file.isActive() || file.getStatus() != MediaStatus.UPLOADED) {
                throw new BusinessException(MediaErrorCode.MEDIA_NOT_UPLOADED);
            }
        }
        files.forEach(MediaFile::attach);
    }

    @Transactional
    public void attachPrivateEvidence(List<Long> mediaIds, long userId) {
        attach(mediaIds, userId);
        if (mediaIds != null) mediaIds.forEach(id -> requireMedia(id).makePrivate());
    }

    @Transactional(readOnly = true)
    public List<Long> findAvailableEvidence(List<Long> mediaIds) {
        return repository.findAllById(mediaIds).stream()
                .filter(media -> media.isActive() && media.getStatus() == MediaStatus.ATTACHED)
                .map(MediaFile::getId)
                .toList();
    }

    /** Caller must authorize Admin access and verify that this media belongs to the report. */
    @Transactional(readOnly = true)
    public MediaContent readEvidenceForAdmin(long mediaId) {
        MediaFile media = requireMedia(mediaId);
        if (!media.isActive() || media.getStatus() != MediaStatus.ATTACHED) {
            throw new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND);
        }
        return readContent(media);
    }

    @Transactional
    public void detach(List<Long> mediaIds, long userId) {
        mediaIds.stream().sorted().map(this::requireMediaForUpdate).forEach(file -> {
            requireOwner(file, userId);
            file.detach();
        });
    }

    /** Temporary gateway for hiding media without changing its status. */
    public void deactivate(long mediaId) {
        throw unsupported("deactivate");
    }

    /** Temporary gateway for soft deletion. */
    public void remove(long mediaId) {
        throw unsupported("remove");
    }

    /**
     * Đọc bytes của ảnh PUBLIC đang dùng. Ném MEDIA_NOT_FOUND nếu không có bản ghi hoặc ảnh bị ẩn,
     * MEDIA_FILE_MISSING nếu bản ghi còn nhưng tệp không còn trong storage, MEDIA_STORAGE_ERROR khi storage lỗi.
     */
    @Transactional(readOnly = true)
    public MediaContent readPublic(long mediaId) {
        MediaFile media = requireMedia(mediaId);
        if (!media.isActive() || !"PUBLIC".equals(media.getVisibility())) {
            throw new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND);
        }
        return readContent(media);
    }

    private MediaContent readContent(MediaFile media) {
        long mediaId = media.getId();
        try {
            return new MediaContent(storage.read(media.getStorageKey()), media.getMimeType());
        } catch (NoSuchFileException exception) {
            // Bản ghi còn nhưng tệp đã mất (ví dụ ổ đĩa Render bị xoá): lỗi dữ liệu, không phải sự cố máy chủ.
            log.warn("Media file missing in storage: mediaId={}, storageKey={}", mediaId, media.getStorageKey());
            throw new BusinessException(MediaErrorCode.MEDIA_FILE_MISSING);
        } catch (IOException exception) {
            log.error(
                    "Cannot read media from storage: mediaId={}, storageKey={}",
                    mediaId,
                    media.getStorageKey(),
                    exception);
            throw new BusinessException(MediaErrorCode.MEDIA_STORAGE_ERROR);
        }
    }

    private MediaFile requireMedia(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND));
    }

    private MediaFile requireMediaForUpdate(long id) {
        return repository.findForUpdate(id).orElseThrow(() -> new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND));
    }

    private void requireOwner(MediaFile media, long userId) {
        if (media.getCreatedBy() == null || media.getCreatedBy() != userId) {
            throw new BusinessException(MediaErrorCode.MEDIA_NOT_OWNER);
        }
    }

    private UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException("MediaService." + operation + " is not implemented yet");
    }

    public record MediaContent(byte[] bytes, String mimeType) {}

    private record ImageFormat(String mimeType, String extension) {
        private static ImageFormat detect(byte[] bytes) {
            if (bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff) {
                return new ImageFormat("image/jpeg", "jpg");
            }
            if (bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4e
                    && bytes[3] == 0x47
                    && bytes[4] == 0x0d
                    && bytes[5] == 0x0a
                    && bytes[6] == 0x1a
                    && bytes[7] == 0x0a) {
                return new ImageFormat("image/png", "png");
            }
            if (bytes.length >= 12
                    && bytes[0] == 'R'
                    && bytes[1] == 'I'
                    && bytes[2] == 'F'
                    && bytes[3] == 'F'
                    && bytes[8] == 'W'
                    && bytes[9] == 'E'
                    && bytes[10] == 'B'
                    && bytes[11] == 'P') {
                return new ImageFormat("image/webp", "webp");
            }
            return null;
        }
    }
}
