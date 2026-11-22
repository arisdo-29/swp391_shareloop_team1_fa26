package com.shareloop.review.service;

import com.shareloop.review.repository.ReviewItemRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phần chỉ đọc của module review cho module khác gọi. Tách khỏi ReviewService vì ReviewService đã phụ thuộc
 * ItemService, nếu ItemService gọi ngược lại sẽ thành vòng.
 */
@Service
@Transactional(readOnly = true)
public class ReviewQueryService {

    private final ReviewItemRepository reviewItemRepository;

    public ReviewQueryService(ReviewItemRepository reviewItemRepository) {
        this.reviewItemRepository = reviewItemRepository;
    }

    /** Lý do Admin từ chối lần gần nhất của bài; rỗng khi bài chưa từng bị từ chối hoặc đã được duyệt lại. */
    public Optional<String> findRejectReason(long itemId) {
        return reviewItemRepository.findById(itemId).map(reviewItem -> reviewItem.getRejectReason());
    }
}
