package com.shareloop.review.controller;

import com.shareloop.review.dto.ApproveReviewRequest;
import com.shareloop.review.dto.RejectReviewRequest;
import com.shareloop.review.dto.ReviewQueueItem;
import com.shareloop.review.dto.ReviewQueueResponse;
import com.shareloop.review.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/items/pending")
    public List<ReviewQueueItem> listPending(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reviewService.listPending(page, size);
    }

    @GetMapping("/reviews/pending")
    public ReviewQueueResponse listPendingReviews(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reviewService.listPendingWithChecklist(page, size);
    }

    @PostMapping("/reviews/{itemId}/approve")
    public ResponseEntity<Void> approve(@PathVariable long itemId, @Valid @RequestBody ApproveReviewRequest request) {
        reviewService.approve(itemId, request.answers());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reviews/reject-reasons")
    public List<String> listRejectReasons() {
        return reviewService.listRejectReasons();
    }

    @PostMapping("/reviews/{itemId}/reject")
    public ResponseEntity<Void> reject(@PathVariable long itemId, @Valid @RequestBody RejectReviewRequest request) {
        reviewService.reject(itemId, request.reason());
        return ResponseEntity.noContent().build();
    }
}
