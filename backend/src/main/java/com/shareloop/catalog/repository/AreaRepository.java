package com.shareloop.catalog.repository;

import com.shareloop.catalog.entity.Area;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaRepository extends JpaRepository<Area, Long> {

    List<Area> findByIsActiveTrueOrderByIdAsc();
}
