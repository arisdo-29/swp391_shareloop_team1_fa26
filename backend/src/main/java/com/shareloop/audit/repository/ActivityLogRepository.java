package com.shareloop.audit.repository;

import com.shareloop.audit.entity.ActivityLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long>, JpaSpecificationExecutor<ActivityLog> {

    long countByType(String type);

    List<ActivityLog> findByType(String type);

    // Dòng nhật ký mới nhất của một loại hành động trên một đối tượng, dùng để đọc lại dữ liệu lúc khoá tài khoản
    java.util.Optional<ActivityLog> findFirstByTypeAndTargetIdOrderByIdDesc(String type, Long targetId);
}
