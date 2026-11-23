package com.shareloop.media.repository;

import com.shareloop.media.entity.MediaFile;
import com.shareloop.media.entity.MediaStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {
    /** Số ảnh một người đã tải lên từ mốc {@code since}, để giới hạn tải ảnh mỗi giờ (D2). */
    long countByCreatedByAndCreatedAtAfter(Long createdBy, Instant since);

    /** Ảnh ở trạng thái cho trước tạo trước mốc {@code before}, cũ trước, dùng cho job dọn ảnh chưa gắn (D2). */
    List<MediaFile> findByStatusAndCreatedAtBeforeOrderByIdAsc(MediaStatus status, Instant before, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaFile m where m.id = :id")
    Optional<MediaFile> findForUpdate(@Param("id") Long id);
}
