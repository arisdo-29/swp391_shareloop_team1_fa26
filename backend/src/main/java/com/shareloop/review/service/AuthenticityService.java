package com.shareloop.review.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.review.ReviewErrorCode;
import com.shareloop.review.entity.ReviewItem;
import com.shareloop.review.repository.ReviewItemRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthenticityService {

    private final ReviewItemRepository reviewItemRepository;
    private final ActivityLogService activityLogService;

    public AuthenticityService(ReviewItemRepository reviewItemRepository, ActivityLogService activityLogService) {
        this.reviewItemRepository = reviewItemRepository;
        this.activityLogService = activityLogService;
    }

    public void grant(long itemId, Long adminId) {
        ReviewItem item = requireItemForUpdate(itemId);
        item.grantAuthenticity();

        activityLogService.log(
                ActivityType.AUTH_GRANT.name(), adminId, "ITEM", itemId, (Map<String, Object>) null, null);
    }

    public void reject(long itemId, String reason, Long adminId) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ReviewErrorCode.AUTHENTICITY_REASON_REQUIRED);
        }

        ReviewItem item = requireItemForUpdate(itemId);
        item.rejectAuthenticity();

        activityLogService.log(
                ActivityType.AUTH_REJECT.name(), adminId, "ITEM", itemId, (Map<String, Object>) null, reason.trim());
    }

    public void revoke(long itemId, String reason, Long adminId) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ReviewErrorCode.AUTHENTICITY_REASON_REQUIRED);
        }

        ReviewItem item = requireItemForUpdate(itemId);
        item.revokeAuthenticity();

        activityLogService.log(
                ActivityType.AUTH_REVOKE.name(), adminId, "ITEM", itemId, (Map<String, Object>) null, reason.trim());
    }

    private ReviewItem requireItemForUpdate(long itemId) {
        return reviewItemRepository
                .findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.AUTHENTICITY_ITEM_NOT_FOUND));
    }
}
