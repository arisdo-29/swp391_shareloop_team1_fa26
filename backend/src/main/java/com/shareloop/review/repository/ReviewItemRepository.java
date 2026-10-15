package com.shareloop.review.repository;

import com.shareloop.review.entity.ReviewItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long> {

    @Query(value = "SELECT donor_id FROM items WHERE id = :itemId AND is_deleted = false", nativeQuery = true)
    Optional<Long> findDonorIdByItemId(@Param("itemId") long itemId);
}
