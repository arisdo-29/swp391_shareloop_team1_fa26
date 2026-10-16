package com.shareloop.audit.repository;

import com.shareloop.audit.entity.ActivityLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    long countByType(String type);

    List<ActivityLog> findByType(String type);
}
