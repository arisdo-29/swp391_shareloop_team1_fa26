package com.shareloop.media.repository;

import com.shareloop.media.entity.MediaFile;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaFile m where m.id = :id")
    Optional<MediaFile> findForUpdate(@Param("id") Long id);
}
