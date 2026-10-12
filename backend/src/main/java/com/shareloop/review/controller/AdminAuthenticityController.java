package com.shareloop.review.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.review.dto.AuthenticityDecisionResponse;
import com.shareloop.review.dto.AuthenticityReviewRequest;
import com.shareloop.review.dto.RevokeAuthenticityRequest;
import com.shareloop.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/items")
public class AdminAuthenticityController {

    private final ReviewService reviewService;

    public AdminAuthenticityController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/{itemId}/auth-verify")
    public AuthenticityDecisionResponse verifyAuthenticity(
            @PathVariable Long itemId,
            @CurrentUserId Long adminId,
            @Valid @RequestBody AuthenticityReviewRequest request) {
        return reviewService.reviewAuthenticity(itemId, adminId, request);
    }

    @PostMapping("/{itemId}/auth-revoke")
    public AuthenticityDecisionResponse revokeAuthenticity(
            @PathVariable Long itemId,
            @CurrentUserId Long adminId,
            @Valid @RequestBody RevokeAuthenticityRequest request) {
        return reviewService.revokeAuthenticity(itemId, adminId, request);
    }
}
