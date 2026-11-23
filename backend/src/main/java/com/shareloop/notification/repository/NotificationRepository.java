package com.shareloop.notification.repository;

import com.shareloop.notification.entity.Notification;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    List<Notification> findByUserIdAndTypeAndLinkAndIsReadFalse(Long userId, String type, String link);

    List<Notification> findByUserIdAndIdIn(Long userId, Collection<Long> ids);

    boolean existsByUserIdAndTypeAndLinkAndCreatedAtGreaterThanEqual(
            Long userId, String type, String link, Instant since);
}
