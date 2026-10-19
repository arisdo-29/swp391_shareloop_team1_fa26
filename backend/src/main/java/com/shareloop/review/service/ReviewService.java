package com.shareloop.review.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemService;
import com.shareloop.listingfee.service.PostFeeService;
import com.shareloop.moderation.ModerationReason;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.review.ReviewErrorCode;
import com.shareloop.review.dto.ChecklistAnswer;
import com.shareloop.review.dto.ReviewQueueItem;
import com.shareloop.review.dto.ReviewQueueResponse;
import com.shareloop.review.entity.ReviewItem;
import com.shareloop.review.repository.ReviewItemRepository;
import com.shareloop.review.repository.ReviewQueueRepository;
import com.shareloop.setting.dto.ChecklistItem;
import com.shareloop.setting.service.ConfigService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final ReviewQueueRepository repository;
    private final ReviewItemRepository reviewItemRepository;
    private final ConfigService configService;
    private final PostFeeService postFeeService;
    private final ItemService itemService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final ObjectMapper objectMapper;

    public ReviewService(
            ReviewQueueRepository repository,
            ReviewItemRepository reviewItemRepository,
            ConfigService configService,
            PostFeeService postFeeService,
            ItemService itemService,
            NotificationService notificationService,
            ActivityLogService activityLogService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.reviewItemRepository = reviewItemRepository;
        this.configService = configService;
        this.postFeeService = postFeeService;
        this.itemService = itemService;
        this.notificationService = notificationService;
        this.activityLogService = activityLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * Trang hàng chờ duyệt. Lý do gắn cờ được đổi sang câu tiếng Việt (ModerationReason), mã gốc vẫn trả ở
     * flagReasonCode; với LEXICON_REVIEW câu hiển thị kèm danh sách từ khoá đã khớp.
     */
    public List<ReviewQueueItem> listPending(int page, int size) {
        return repository.findPendingReviewQueue(PageRequest.of(page, size)).stream()
                .map(this::toQueueItem)
                .toList();
    }

    public ReviewQueueResponse listPendingWithChecklist(int page, int size) {
        return new ReviewQueueResponse(listPending(page, size), configService.checklistItems(), page, size);
    }

    public List<String> listRejectReasons() {
        return configService.rejectReasons();
    }

    /**
     * Duyệt bài: đáp án ghép theo id mục checklist, phải đủ mọi mục đang dùng, không trùng và đều đạt.
     * Ném REVIEW_CHECKLIST_NOT_CONFIGURED, REVIEW_CHECKLIST_INCOMPLETE hoặc REVIEW_CHECKLIST_OUTDATED (có id không
     * còn thuộc checklist đang dùng). Snapshot lưu nguyên câu điều kiện tại thời điểm duyệt.
     */
    @Transactional
    public void approve(long itemId, List<ChecklistAnswer> answers, Long adminId) {
        List<ChecklistItem> checklistItems = configService.checklistItems();
        validateAnswers(checklistItems, answers);

        postFeeService.chargeHeldFee(itemId);
        itemService.markApproved(itemId);

        Map<String, Object> snapshot = buildSnapshot(checklistItems);
        requireReviewItem(itemId).approve(snapshot, Instant.now(), adminId);

        activityLogService.log(ActivityType.REVIEW_APPROVE.name(), adminId, "ITEM", itemId, snapshot, null);
    }

    @Transactional
    public void reject(long itemId, String reason) {
        reject(itemId, reason, null);
    }

    @Transactional
    public void reject(long itemId, String reason, Long adminId) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ReviewErrorCode.REVIEW_REJECT_REASON_REQUIRED);
        }

        postFeeService.releaseHeldFee(itemId, "REJECTED");
        itemService.markRejected(itemId);
        requireReviewItem(itemId).reject(reason.trim(), Instant.now(), adminId);

        reviewItemRepository
                .findDonorIdByItemId(itemId)
                .ifPresent(donorId -> notificationService.notify(
                        donorId, "POST_REJECTED", "Bài đăng bị từ chối", reason.trim(), "/items/" + itemId));

        activityLogService.log(
                ActivityType.REVIEW_REJECT.name(), adminId, "ITEM", itemId, (Map<String, Object>) null, reason.trim());
    }

    // Mỗi bước kiểm một điều kiện riêng để mã lỗi trả về chỉ đúng nguyên nhân.
    private void validateAnswers(List<ChecklistItem> checklistItems, List<ChecklistAnswer> answers) {
        if (checklistItems.isEmpty()) {
            throw new BusinessException(ReviewErrorCode.REVIEW_CHECKLIST_NOT_CONFIGURED);
        }
        if (answers == null || answers.isEmpty()) {
            throw new BusinessException(ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);
        }

        Set<Long> answeredIds = collectAnsweredIds(answers);

        Set<Long> activeIds = new HashSet<>();
        for (ChecklistItem checklistItem : checklistItems) {
            activeIds.add(checklistItem.id());
        }
        // Id lạ nghĩa là trang của Admin đang giữ checklist cũ (mục vừa bị ẩn/xoá), cần tải lại.
        if (!activeIds.containsAll(answeredIds)) {
            throw new BusinessException(ReviewErrorCode.REVIEW_CHECKLIST_OUTDATED);
        }
        if (!answeredIds.containsAll(activeIds)) {
            throw new BusinessException(ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);
        }

        for (ChecklistAnswer answer : answers) {
            if (!Boolean.TRUE.equals(answer.passed())) {
                throw new BusinessException(ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);
            }
        }
    }

    // Gom id đã trả lời; gặp id null hoặc id trùng thì coi như checklist chưa được trả lời đúng.
    private Set<Long> collectAnsweredIds(List<ChecklistAnswer> answers) {
        Set<Long> answeredIds = new HashSet<>();
        for (ChecklistAnswer answer : answers) {
            if (answer == null || answer.id() == null) {
                throw new BusinessException(ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);
            }
            boolean isNewId = answeredIds.add(answer.id());
            if (!isNewId) {
                throw new BusinessException(ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);
            }
        }
        return answeredIds;
    }

    // Lưu theo thứ tự checklist (không theo thứ tự client gửi); passed luôn true vì validateAnswers đã kiểm.
    private Map<String, Object> buildSnapshot(List<ChecklistItem> checklistItems) {
        List<Map<String, Object>> checks = new ArrayList<>(checklistItems.size());
        for (ChecklistItem checklistItem : checklistItems) {
            Map<String, Object> check = new LinkedHashMap<>();
            check.put("id", checklistItem.id());
            check.put("key", checklistItem.key());
            check.put("statement", checklistItem.statement());
            check.put("passed", true);
            checks.add(check);
        }
        return Map.of("checks", checks);
    }

    private ReviewQueueItem toQueueItem(Object[] row) {
        String flagReasonCode = (String) row[5];
        List<String> matchedTerms = parseMatchedTerms((String) row[8]);
        return new ReviewQueueItem(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                (String) row[2],
                (String) row[3],
                (String) row[4],
                flagReasonCode,
                describeFlagReason(flagReasonCode, matchedTerms),
                matchedTerms,
                ((Number) row[6]).shortValue(),
                toInstant(row[7]));
    }

    private String describeFlagReason(String flagReasonCode, List<String> matchedTerms) {
        String description = ModerationReason.describe(flagReasonCode);
        boolean isLexiconReview = ModerationReason.LEXICON_REVIEW.name().equals(flagReasonCode);
        if (isLexiconReview && !matchedTerms.isEmpty()) {
            return description + ": " + String.join(", ", matchedTerms);
        }
        return description;
    }

    // Bài gắn cờ trước khi có matchedTerms, hoặc bài không có cờ, sẽ trả null -> danh sách rỗng.
    private List<String> parseMatchedTerms(String matchedTermsJson) {
        if (matchedTermsJson == null || matchedTermsJson.isBlank()) {
            return List.of();
        }
        List<String> matchedTerms = objectMapper.readValue(matchedTermsJson, STRING_LIST);
        return matchedTerms == null ? List.of() : matchedTerms;
    }

    private ReviewItem requireReviewItem(long itemId) {
        return reviewItemRepository
                .findById(itemId)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_ITEM_NOT_FOUND));
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException(
                "Unsupported timestamp result type: " + value.getClass().getName());
    }
}
