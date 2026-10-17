package com.shareloop.request.repository;

import com.shareloop.request.entity.RequestMediaMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository thao tác bảng request_media_mappings (FR-86, BR-T09).
 */
@Repository
public interface RequestMediaRepository extends JpaRepository<RequestMediaMapping, RequestMediaMapping.RequestMediaId> {

    List<RequestMediaMapping> findByRequestIdOrderByCreatedAtAsc(Long requestId);

    List<RequestMediaMapping> findByRequestIdAndStageOrderByCreatedAtAsc(Long requestId, String stage);

    boolean existsByRequestIdAndMediaId(Long requestId, Long mediaId);
}
