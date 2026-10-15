package com.shareloop.catalog.repository;

import com.shareloop.catalog.entity.Area;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaRepository extends JpaRepository<Area, Long> {

    List<Area> findByIsActiveTrueOrderByIdAsc();

    // Khu vực đã xoá mềm bị @SQLRestriction loại nên chỉ cần kiểm thêm is_active.
    boolean existsByIdAndIsActiveTrue(Long id);
}
