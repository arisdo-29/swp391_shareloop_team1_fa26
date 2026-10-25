package com.shareloop.review.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.catalog.service.AreaService;
import com.shareloop.catalog.service.CategoryAttributeService;
import com.shareloop.catalog.service.CategoryService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.AdminItemDetail;
import com.shareloop.item.dto.ItemAttributeValue;
import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.service.ItemService;
import com.shareloop.listingfee.service.PostFeeService;
import com.shareloop.moderation.ModerationReason;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.review.ReviewErrorCode;
import com.shareloop.review.dto.ChecklistAnswer;
import com.shareloop.review.dto.ReviewItemAttribute;
import com.shareloop.review.dto.ReviewItemDetailResponse;
import com.shareloop.review.dto.ReviewQueueItem;
import com.shareloop.review.dto.ReviewQueueResponse;
import com.shareloop.review.entity.ReviewItem;
import com.shareloop.review.repository.ReviewItemRepository;
import com.shareloop.review.repository.ReviewQueueRepository;
import com.shareloop.setting.dto.ChecklistItem;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final UserService userService;
    private final CategoryService categoryService;
    private final AreaService areaService;
    private final CategoryAttributeService categoryAttributeService;
    private final ObjectMapper objectMapper;

    public ReviewService(
            ReviewQueueRepository repository,
            ReviewItemRepository reviewItemRepository,
            ConfigService configService,
            PostFeeService postFeeService,
            ItemService itemService,
            NotificationService notificationService,
            ActivityLogService activityLogService,
            UserService userService,
            CategoryService categoryService,
            AreaService areaService,
            CategoryAttributeService categoryAttributeService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.reviewItemRepository = reviewItemRepository;
        this.configService = configService;
        this.postFeeService = postFeeService;
        this.itemService = itemService;
        this.notificationService = notificationService;
        this.activityLogService = activityLogService;
        this.userService = userService;
        this.categoryService = categoryService;
        this.areaService = areaService;
        this.categoryAttributeService = categoryAttributeService;
        this.objectMapper = objectMapper;
    }

    /**
     * Trang hàng chờ duyệt. Lý do gắn cờ được đổi sang câu tiếng Việt (ModerationReason), mã gốc vẫn trả ở
     * flagReasonCode; với LEXICON_REVIEW câu hiển thị kèm danh sách từ khoá đã khớp.
     */
    public List<ReviewQueueItem> listPending(int page, int size) {
        List<Object[]> rows = repository.findPendingReviewQueue(PageRequest.of(page, size));
        Map<Long, UserBrief> donors = findDonors(rows);

        List<ReviewQueueItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(toQueueItem(row, donors));
        }
        return items;
    }

    // Tên người đăng nằm ở module user nên hỏi một lần cho cả trang; cột 1 của mỗi dòng là donor_id.
    private Map<Long, UserBrief> findDonors(List<Object[]> rows) {
        Set<Long> donorIds = new HashSet<>();
        for (Object[] row : rows) {
            donorIds.add(((Number) row[1]).longValue());
        }
        return userService.findBriefs(donorIds);
    }

    public ReviewQueueResponse listPendingWithChecklist(int page, int size) {
        return new ReviewQueueResponse(listPending(page, size), configService.checklistItems(), page, size);
    }

    /**
     * Chi tiết một bài cho Admin duyệt, ở mọi trạng thái (kể cả is_active = false), kèm checklist hiện hành.
     * Ném REVIEW_ITEM_NOT_FOUND khi bài không tồn tại hoặc đã xoá mềm.
     */
    public ReviewItemDetailResponse getDetail(long itemId) {
        ReviewItem reviewItem = requireReviewItem(itemId);
        AdminItemDetail detail = itemService.getItemForAdmin(itemId);
        Object[] moderation = requireModerationInfo(itemId);
        return toDetailResponse(detail, reviewItem, moderation);
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
        ReviewItem reviewItem = requireReviewItem(itemId);
        List<ChecklistItem> checklistItems = configService.checklistItems();
        validateAnswers(checklistItems, answers);

        // Đổi trạng thái TRƯỚC khi trừ phí: bài đã xử lý báo 409 ITEM_INVALID_STATUS_TRANSITION chứ không phải lỗi phí.
        itemService.markApproved(itemId);
        postFeeService.chargeHeldFee(itemId);

        Map<String, Object> snapshot = buildSnapshot(checklistItems);
        reviewItem.approve(snapshot, Instant.now(), adminId);

        // UC-40 bước 5: duyệt xong báo người đăng, giống nhánh từ chối.
        reviewItemRepository
                .findDonorIdByItemId(itemId)
                .ifPresent(donorId -> notificationService.notify(
                        donorId,
                        "POST_APPROVED",
                        "Bài đăng đã được duyệt",
                        "Bài đăng của bạn đã được duyệt và đang hiển thị công khai.",
                        "/items/" + itemId));

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

        ReviewItem reviewItem = requireReviewItem(itemId);

        // Cùng thứ tự với approve: kiểm trạng thái trước, rồi mới nhả phí.
        itemService.markRejected(itemId);
        postFeeService.releaseHeldFee(itemId, "REJECTED");
        reviewItem.reject(reason.trim(), Instant.now(), adminId);

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

    private ReviewQueueItem toQueueItem(Object[] row, Map<Long, UserBrief> donors) {
        long donorId = ((Number) row[1]).longValue();
        UserBrief donor = donors.get(donorId);
        String flagReasonCode = (String) row[5];
        List<String> matchedTerms = parseMatchedTerms((String) row[8]);
        return new ReviewQueueItem(
                ((Number) row[0]).longValue(),
                donorId,
                donor == null ? null : donor.fullName(),
                (String) row[2],
                (String) row[3],
                (String) row[4],
                flagReasonCode,
                describeFlagReason(flagReasonCode, matchedTerms),
                matchedTerms,
                ((Number) row[6]).shortValue(),
                toInstant(row[7]));
    }

    private ReviewItemDetailResponse toDetailResponse(
            AdminItemDetail detail, ReviewItem reviewItem, Object[] moderation) {
        ItemResponse item = detail.item();
        String flagReasonCode = (String) moderation[2];
        List<String> matchedTerms = parseMatchedTerms((String) moderation[4]);
        Optional<UserBrief> donor = Optional.ofNullable(
                userService.findBriefs(List.of(item.donorId())).get(item.donorId()));

        return new ReviewItemDetailResponse(
                item.id(),
                item.title(),
                item.description(),
                item.desiredItem(),
                item.offerType(),
                item.condition(),
                item.defectNote(),
                item.brand(),
                item.status(),
                detail.active(),
                item.operatingStatus(),
                item.images(),
                item.defectImages(),
                item.categoryId(),
                categoryService.findName(item.categoryId()).orElse(null),
                item.areaId(),
                areaService.findName(item.areaId()).orElse(null),
                item.donorId(),
                donor.map(UserBrief::fullName).orElse(null),
                ((Number) moderation[3]).shortValue(),
                toAttributes(detail.attributes()),
                (String) moderation[0],
                (String) moderation[1],
                flagReasonCode,
                describeFlagReason(flagReasonCode, matchedTerms),
                matchedTerms,
                reviewItem.getAuthStatus().name(),
                configService.checklistItems(),
                item.createdAt(),
                item.updatedAt());
    }

    // Tên thuộc tính nằm ở module catalog nên hỏi một lần cho cả danh sách; thuộc tính đã xoá thì name = null.
    private List<ReviewItemAttribute> toAttributes(List<ItemAttributeValue> values) {
        List<Long> attributeIds = new ArrayList<>();
        for (ItemAttributeValue value : values) {
            attributeIds.add(value.attributeId());
        }
        Map<Long, String> names = categoryAttributeService.findNames(attributeIds);

        List<ReviewItemAttribute> attributes = new ArrayList<>();
        for (ItemAttributeValue value : values) {
            attributes.add(new ReviewItemAttribute(value.attributeId(), names.get(value.attributeId()), value.value()));
        }
        return attributes;
    }

    private Object[] requireModerationInfo(long itemId) {
        List<Object[]> rows = repository.findModerationInfo(itemId);
        if (rows.isEmpty()) {
            throw new BusinessException(ReviewErrorCode.REVIEW_ITEM_NOT_FOUND);
        }
        return rows.get(0);
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
