package com.shareloop.review.repository;

import com.shareloop.review.entity.ReviewItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long> {}
