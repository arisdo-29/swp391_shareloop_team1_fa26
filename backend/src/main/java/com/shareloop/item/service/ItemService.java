package com.shareloop.item.service;

import com.shareloop.catalog.service.CategoryAttributeService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.AdminItemDetail;
import com.shareloop.item.dto.CreateItemRequest;
import com.shareloop.item.dto.ItemAttributeValue;
import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.dto.UpdateItemRequest;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.entity.OperatingStatus;
import com.shareloop.item.mapper.ItemMapper;
import com.shareloop.item.repository.ItemRepository;
import com.shareloop.listingfee.dto.DisplayWindow;
import com.shareloop.listingfee.service.ListingFeeQueryService;
import com.shareloop.listingfee.service.PostFeeService;
import com.shareloop.media.MediaErrorCode;
import com.shareloop.media.service.MediaService;
import com.shareloop.moderation.ModerationResult;
import com.shareloop.moderation.ModerationStatus;
import com.shareloop.moderation.event.ItemScreeningRequestedEvent;
import com.shareloop.moderation.service.ContentModerationService;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.service.RequestQueryService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Service quản lý bài đăng: tạo mới, xem chi tiết, xem danh sách bài của tôi.
 */
@Service
public class ItemService {
    private static final Pattern MEDIA_URL = Pattern.compile("/api/v1/media/([1-9][0-9]*)");
    private final ItemRepository itemRepository;
    private final JdbcClient jdbcClient;
    private final MediaService mediaService;
    private final ConfigService configService;
    private final RequestQueryService requestQueryService;
    private final ContentModerationService contentModerationService;
    private final PostFeeService postFeeService;
    private final ListingFeeQueryService listingFeeQueryService;
    private final CategoryAttributeService categoryAttributeService;
    private final ReputationService reputationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public ItemService(
            ItemRepository itemRepository,
            @Autowired(required = false) JdbcClient jdbcClient,
            MediaService mediaService,
            ConfigService configService,
            RequestQueryService requestQueryService,
            ContentModerationService contentModerationService,
            PostFeeService postFeeService,
            ListingFeeQueryService listingFeeQueryService,
            CategoryAttributeService categoryAttributeService,
            ReputationService reputationService,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.itemRepository = itemRepository;
        this.jdbcClient = jdbcClient;
        this.mediaService = mediaService;
        this.configService = configService;
        this.requestQueryService = requestQueryService;
        this.contentModerationService = contentModerationService;
        this.postFeeService = postFeeService;
        this.listingFeeQueryService = listingFeeQueryService;
        this.categoryAttributeService = categoryAttributeService;
        this.reputationService = reputationService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Tạo bài đăng mới ở trạng thái PENDING_REVIEW (F07 bước 1).
     * Người có 0 sao uy tín không được đăng bài (FR-92, BR-U05): ném ITEM_REPUTATION_BLOCKED.
     */
    @Transactional
    public ItemResponse createItem(Long donorId, CreateItemRequest request) {
        // Kiểm tra sao uy tín đầu tiên để không giữ phí hay kiểm duyệt cho người đã bị khoá quyền đăng bài
        requireCanPost(donorId);

        // SWAP bắt buộc phải có món mong muốn (BR-S02, ck_items_swap_desired)
        if (request.offerType() == OfferType.SWAP
                && (request.desiredItem() == null || request.desiredItem().isBlank())) {
            throw new BusinessException(ItemErrorCode.ITEM_SWAP_DESIRED_ITEM_REQUIRED);
        }

        // Đồ lỗi bắt buộc phải có mô tả lỗi (ck_items_defect_note)
        if (request.condition() == ItemCondition.DEFECTIVE
                && (request.defectNote() == null || request.defectNote().isBlank())) {
            throw new BusinessException(ItemErrorCode.ITEM_DEFECT_NOTE_REQUIRED);
        }
        List<Long> defectMediaIds = parseMediaIds(request.defectImages());
        validateDefectImages(request.condition(), defectMediaIds);
        Optional<Long> operatingAttributeId = requireOperatingStatus(request.categoryId(), request.operatingStatus());
        checkActiveItemLimit(donorId);

        String moderationText = request.desiredItem() == null
                ? request.description()
                : request.description() + " " + request.desiredItem();
        var moderation = contentModerationService.checkPost(request.categoryId(), request.title(), moderationText);
        if (moderation.status() == ModerationStatus.BLOCK) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_MODERATION_BLOCKED,
                    ItemErrorCode.ITEM_MODERATION_BLOCKED.defaultMessage(),
                    "content",
                    Map.of("reasonCode", moderation.reasonCode(), "matchedTerms", moderation.matchedTerms()));
        }

        Item item = new Item(
                donorId,
                request.categoryId(),
                request.areaId(),
                request.offerType(),
                request.title(),
                request.description(),
                request.desiredItem(),
                request.condition(),
                request.defectNote(),
                request.brand());

        int minImages = configService.getInt(ConfigKey.ITEM_PHOTOS_MIN);
        int maxImages = configService.getInt(ConfigKey.ITEM_PHOTOS_MAX);
        if (request.images() == null
                || request.images().size() < minImages
                || request.images().size() > maxImages) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_INVALID_IMAGE_COUNT,
                    "Bài đăng cần từ %d đến %d ảnh.".formatted(minImages, maxImages),
                    "images",
                    Map.of("min", minImages, "max", maxImages));
        }
        List<Long> mediaIds = parseMediaIds(request.images());
        List<Long> allMediaIds = combineMediaIds(mediaIds, defectMediaIds);
        // PostFeeService reads the new item through JDBC in the same transaction.
        Item saved = itemRepository.saveAndFlush(item);
        postFeeService.holdPostFee(saved.getId(), donorId);
        mediaService.attach(allMediaIds, donorId);
        saveMediaMappings(saved.getId(), mediaIds, "PHOTO");
        saveMediaMappings(saved.getId(), defectMediaIds, "DEFECT");
        operatingAttributeId.ifPresent(id -> saveOperatingStatus(saved.getId(), id, request.operatingStatus()));
        if (moderation.status() == ModerationStatus.REVIEW) {
            saveReviewFlag(saved.getId(), moderation);
        }
        eventPublisher.publishEvent(ItemScreeningRequestedEvent.from(saved));
        return ItemMapper.toResponse(
                saved,
                mediaUrls(mediaIds),
                mediaUrls(defectMediaIds),
                request.operatingStatus(),
                findDisplayWindow(saved.getId()));
    }

    /**
     * Gửi bản sửa của bài, bài quay về PENDING_REVIEW (F07 bước 4); phí sửa do PostFeeService.holdEditFee quyết.
     * Không cho đổi bài Cho tặng sang Trao đổi (né phí đăng): ném ITEM_OFFER_TYPE_CHANGE_NOT_ALLOWED.
     */
    @Transactional
    public ItemResponse updateItem(long itemId, long userId, UpdateItemRequest request) {
        Item item = requireItem(itemId);
        if (item.getDonorId() != userId) {
            throw new BusinessException(ItemErrorCode.ITEM_NOT_OWNER);
        }
        // Bài của người 0 sao đã bị ẩn, sửa sẽ tốn phí sửa cho bài không hiển thị được (BR-U05)
        requireCanPost(userId);
        if (item.getStatus() == ItemStatus.RESERVED
                || item.getStatus() == ItemStatus.TRADED
                || item.getStatus() == ItemStatus.EXPIRED
                || requestQueryService.hasReservedOrHigherRequest(itemId)) {
            throw new BusinessException(ItemErrorCode.ITEM_EDIT_NOT_ALLOWED);
        }
        // Bài GIVE miễn phí đăng, bài SWAP mất phí đăng: cho đổi GIVE sang SWAP thì né được phí, nên chặn.
        // Chiều SWAP sang GIVE vẫn cho phép. Kiểm trước khi giữ phí sửa (holdEditFee) để không đụng tới ví.
        if (item.getOfferType() == OfferType.GIVE && request.offerType() == OfferType.SWAP) {
            throw new BusinessException(ItemErrorCode.ITEM_OFFER_TYPE_CHANGE_NOT_ALLOWED);
        }
        validateContent(request.offerType(), request.desiredItem(), request.condition(), request.defectNote());
        String moderationText = request.desiredItem() == null
                ? request.description()
                : request.description() + " " + request.desiredItem();
        var moderation = contentModerationService.checkPost(request.categoryId(), request.title(), moderationText);
        if (moderation.status() == ModerationStatus.BLOCK) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_MODERATION_BLOCKED,
                    ItemErrorCode.ITEM_MODERATION_BLOCKED.defaultMessage(),
                    "content",
                    Map.of("reasonCode", moderation.reasonCode(), "matchedTerms", moderation.matchedTerms()));
        }

        List<Long> newMediaIds = null;
        if (request.images() != null) {
            validateImageCount(request.images());
            newMediaIds = parseMediaIds(request.images());
            if (new HashSet<>(newMediaIds).size() != newMediaIds.size()) {
                throw new BusinessException(MediaErrorCode.MEDIA_INVALID_FILE);
            }
        }
        List<Long> newDefectMediaIds =
                request.defectImages() == null ? findMediaIds(itemId, "DEFECT") : parseMediaIds(request.defectImages());
        if (request.condition() != ItemCondition.DEFECTIVE) {
            if (request.defectImages() != null && !newDefectMediaIds.isEmpty()) {
                throw new BusinessException(
                        ItemErrorCode.ITEM_DEFECT_IMAGES_NOT_ALLOWED,
                        ItemErrorCode.ITEM_DEFECT_IMAGES_NOT_ALLOWED.defaultMessage(),
                        "defectImages",
                        null);
            }
            newDefectMediaIds = List.of();
        }
        validateDefectImages(request.condition(), newDefectMediaIds);
        Optional<Long> operatingAttributeId = requireOperatingStatus(request.categoryId(), request.operatingStatus());
        postFeeService.quoteEdit(itemId, userId);
        postFeeService.holdEditFee(itemId, userId);
        if (requestQueryService.hasReservedOrHigherRequest(itemId)) {
            throw new BusinessException(ItemErrorCode.ITEM_EDIT_NOT_ALLOWED);
        }

        Long previousCategoryId = item.getCategoryId();
        item.submitEdit(
                request.categoryId(),
                request.areaId(),
                request.offerType(),
                request.title(),
                request.description(),
                request.desiredItem(),
                request.condition(),
                request.defectNote(),
                request.brand());
        if (newMediaIds != null || request.defectImages() != null || request.condition() != ItemCondition.DEFECTIVE) {
            replaceMediaMappings(
                    itemId,
                    userId,
                    newMediaIds == null ? findMediaIds(itemId, "PHOTO") : newMediaIds,
                    newDefectMediaIds);
        }
        replaceOperatingStatus(itemId, previousCategoryId, operatingAttributeId, request.operatingStatus());
        jdbcClient
                .sql("UPDATE items SET ai_risk = 'UNCHECKED', ai_result = NULL WHERE id = :itemId")
                .param("itemId", itemId)
                .update();
        if (moderation.status() == ModerationStatus.REVIEW) {
            saveReviewFlag(itemId, moderation);
        }
        eventPublisher.publishEvent(ItemScreeningRequestedEvent.from(item));
        return toResponse(item, findDisplayWindow(itemId));
    }

    /**
     * Xem chi tiết bài đăng:
     * - Bài APPROVED: ai cũng xem được.
     * - Chủ bài đăng: xem được mọi trạng thái.
     * - Còn lại: trả về ITEM_NOT_FOUND (404).
     */
    @Transactional(readOnly = true)
    public ItemResponse getItem(Long id, Long currentUserId) {
        Item item = itemRepository.findById(id).orElseThrow(() -> new BusinessException(ItemErrorCode.ITEM_NOT_FOUND));

        boolean isOwner = currentUserId != null && currentUserId.equals(item.getDonorId());
        if ((item.getStatus() == ItemStatus.APPROVED && item.isActive()) || isOwner) {
            return toResponse(item, findDisplayWindow(item.getId()));
        }

        throw new BusinessException(ItemErrorCode.ITEM_NOT_FOUND);
    }

    /**
     * Admin xem chi tiết bài ở mọi trạng thái (kể cả is_active = false) để duyệt bài PENDING_REVIEW.
     * Cố ý tách khỏi getItem để endpoint công khai không bị nới quyền. Ném ITEM_NOT_FOUND khi bài không tồn tại
     * hoặc đã xoá mềm.
     */
    @Transactional(readOnly = true)
    public AdminItemDetail getItemForAdmin(long itemId) {
        Item item = requireItem(itemId);
        ItemResponse response = toResponse(item, findDisplayWindow(itemId));
        return new AdminItemDetail(response, item.isActive(), findAttributeValues(itemId));
    }

    /**
     * Xem danh sách bài đăng của chính mình.
     */
    @Transactional(readOnly = true)
    public List<ItemResponse> listMyItems(Long currentUserId) {
        List<Item> items = itemRepository.findByDonorIdOrderByCreatedAtDesc(currentUserId);
        Map<Long, DisplayWindow> displayWindows = findDisplayWindows(items);
        List<ItemResponse> responses = new ArrayList<>();
        for (Item item : items) {
            responses.add(toResponse(item, displayWindows.get(item.getId())));
        }
        return responses;
    }

    /**
     * Danh sách công khai bài APPROVED còn hiển thị (FR-50). Mọi bộ lọc đều không bắt buộc và kết hợp được với nhau;
     * lọc ngay ở database để phân trang và tổng số luôn đúng.
     */
    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> listPublicItems(
            Long categoryId, Long areaId, String q, ItemCondition condition, OfferType offerType, int page, int size) {
        if (q == null || q.isBlank()) {
            PageRequest pageRequest =
                    PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
            return toPageResponse(itemRepository.findVisibleByFilters(
                    ItemStatus.APPROVED, categoryId, areaId, condition, offerType, pageRequest));
        }
        // Truy vấn native so sánh theo chuỗi nên đổi enum sang tên
        return toPageResponse(itemRepository.searchVisibleByFilters(
                categoryId,
                areaId,
                condition == null ? null : condition.name(),
                offerType == null ? null : offerType.name(),
                q.trim(),
                PageRequest.of(page, size)));
    }

    /** Search gateway for the AI assistant; the database remains the source of every returned item. */
    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> searchApprovedForAi(
            Long categoryId, Long areaId, String q, ItemCondition condition, OfferType offerType, int page, int size) {
        Page<Item> items = itemRepository.searchApprovedForAi(
                categoryId,
                areaId,
                condition == null ? null : condition.name(),
                offerType == null ? null : offerType.name(),
                q == null ? "" : q.trim(),
                PageRequest.of(page, size));
        return toPageResponse(items);
    }

    /**
     * Giữ chỗ bài đăng khi chủ bài chọn người nhận (BR-D01, Issue #38).
     */
    @Transactional
    public void reserve(Long itemId) {
        transition(itemId, ItemStatus.APPROVED, ItemStatus.RESERVED);
    }

    /**
     * Chuyển trạng thái bài đăng sang TRADED khi Request hoàn tất giao dịch (COMPLETED).
     */
    @Transactional
    public void markTraded(long itemId) {
        Item item = requireItem(itemId);
        if (!ItemStatus.canTransition(item.getStatus(), ItemStatus.TRADED)) {
            throw new BusinessException(ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);
        }
        item.markTraded();
    }

    /** Nhả giữ chỗ sau khi Request bị huỷ. */
    @Transactional
    public void release(long itemId) {
        transition(itemId, ItemStatus.RESERVED, ItemStatus.APPROVED);
    }

    @Transactional
    public void hide(long itemId) {
        requireItem(itemId).deactivate();
    }

    @Transactional
    public void unhide(long itemId) {
        requireItem(itemId).activate();
    }

    @Transactional
    public void remove(long itemId) {
        removeItem(requireItem(itemId));
    }

    @Transactional
    public void remove(long itemId, long userId) {
        Item item = requireItem(itemId);
        if (item.getDonorId() != userId) {
            throw new BusinessException(ItemErrorCode.ITEM_NOT_OWNER);
        }
        removeItem(item);
    }

    private void removeItem(Item item) {
        long itemId = item.getId();
        if (item.getStatus() == ItemStatus.RESERVED
                || item.getStatus() == ItemStatus.TRADED
                || item.getStatus() == ItemStatus.EXPIRED
                || requestQueryService.hasReservedOrHigherRequest(itemId)) {
            throw new BusinessException(ItemErrorCode.ITEM_REMOVE_NOT_ALLOWED);
        }
        if (postFeeService.hasHeldFee(itemId)) {
            postFeeService.releaseHeldFee(itemId, "DELETED");
        }
        item.markDeleted();
    }

    @Transactional
    public void hideAllOfUser(long userId) {
        itemRepository.findByDonorIdAndIsActiveTrue(userId).forEach(Item::deactivate);
    }

    @Transactional
    public void markExpired(long itemId) {
        transition(itemId, ItemStatus.APPROVED, ItemStatus.EXPIRED);
    }

    @Transactional
    public void markApproved(long itemId) {
        transition(itemId, ItemStatus.PENDING_REVIEW, ItemStatus.APPROVED);
    }

    @Transactional
    public void markRejected(long itemId) {
        transition(itemId, ItemStatus.PENDING_REVIEW, ItemStatus.REJECTED);
    }

    private void transition(long itemId, ItemStatus expected, ItemStatus target) {
        Item item = requireItem(itemId);
        if (item.getStatus() != expected || !ItemStatus.canTransition(item.getStatus(), target)) {
            throw new BusinessException(ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);
        }
        item.transitionTo(target);
    }

    private Item requireItem(long itemId) {
        return itemRepository.findById(itemId).orElseThrow(() -> new BusinessException(ItemErrorCode.ITEM_NOT_FOUND));
    }

    private List<Long> parseMediaIds(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return images.stream()
                .map(url -> {
                    Matcher matcher = url == null ? null : MEDIA_URL.matcher(url);
                    if (matcher == null || !matcher.matches()) {
                        throw new BusinessException(ItemErrorCode.ITEM_INVALID_IMAGE);
                    }
                    try {
                        return Long.parseLong(matcher.group(1));
                    } catch (NumberFormatException exception) {
                        throw new BusinessException(ItemErrorCode.ITEM_INVALID_IMAGE);
                    }
                })
                .toList();
    }

    private void validateContent(OfferType offerType, String desiredItem, ItemCondition condition, String defectNote) {
        if (offerType == OfferType.SWAP && (desiredItem == null || desiredItem.isBlank())) {
            throw new BusinessException(ItemErrorCode.ITEM_SWAP_DESIRED_ITEM_REQUIRED);
        }
        if (condition == ItemCondition.DEFECTIVE && (defectNote == null || defectNote.isBlank())) {
            throw new BusinessException(ItemErrorCode.ITEM_DEFECT_NOTE_REQUIRED);
        }
    }

    private void validateImageCount(List<String> images) {
        int minImages = configService.getInt(ConfigKey.ITEM_PHOTOS_MIN);
        int maxImages = configService.getInt(ConfigKey.ITEM_PHOTOS_MAX);
        if (images.size() < minImages || images.size() > maxImages) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_INVALID_IMAGE_COUNT,
                    "Bài đăng cần từ %d đến %d ảnh.".formatted(minImages, maxImages),
                    "images",
                    Map.of("min", minImages, "max", maxImages));
        }
    }

    private void requireCanPost(Long userId) {
        if (!reputationService.canPost(userId)) {
            throw new BusinessException(ItemErrorCode.ITEM_REPUTATION_BLOCKED);
        }
    }

    private void checkActiveItemLimit(Long donorId) {
        Rank rank = reputationService.getRank(donorId);
        ConfigKey limitKey =
                switch (rank) {
                    case BRONZE -> ConfigKey.MAX_ACTIVE_ITEMS_BRONZE;
                    case SILVER -> ConfigKey.MAX_ACTIVE_ITEMS_SILVER;
                    case GOLD -> ConfigKey.MAX_ACTIVE_ITEMS_GOLD;
                    case DIAMOND -> ConfigKey.MAX_ACTIVE_ITEMS_DIAMOND;
                };
        int limit = configService.getInt(limitKey);
        if (limit < 0) {
            return;
        }

        // Serialize submissions by the same owner so concurrent requests cannot exceed the limit.
        jdbcClient
                .sql("SELECT pg_advisory_xact_lock(:ownerId)")
                .param("ownerId", donorId)
                .query()
                .singleRow();
        long activeCount = itemRepository.countByDonorIdAndStatusInAndIsActiveTrue(
                donorId, List.of(ItemStatus.PENDING_REVIEW, ItemStatus.APPROVED));
        if (activeCount >= limit) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_ACTIVE_LIMIT_REACHED,
                    "Bạn chỉ được có tối đa %d bài đang hiển thị hoặc chờ duyệt.".formatted(limit),
                    null,
                    Map.of("limit", limit, "activeCount", activeCount));
        }
    }

    private void replaceMediaMappings(long itemId, long userId, List<Long> photos, List<Long> defects) {
        List<Long> oldMediaIds = jdbcClient
                .sql("SELECT media_id FROM item_media_mappings WHERE item_id = :itemId AND role IN ('PHOTO', 'DEFECT')")
                .param("itemId", itemId)
                .query(Long.class)
                .list();
        List<Long> newMediaIds = combineMediaIds(photos, defects);
        List<Long> added =
                newMediaIds.stream().filter(id -> !oldMediaIds.contains(id)).toList();
        List<Long> removed =
                oldMediaIds.stream().filter(id -> !newMediaIds.contains(id)).toList();
        mediaService.attach(added, userId);
        jdbcClient
                .sql("DELETE FROM item_media_mappings WHERE item_id = :itemId AND role IN ('PHOTO', 'DEFECT')")
                .param("itemId", itemId)
                .update();
        saveMediaMappings(itemId, photos, "PHOTO");
        saveMediaMappings(itemId, defects, "DEFECT");
        mediaService.detach(removed, userId);
    }

    private List<Long> combineMediaIds(List<Long> photos, List<Long> defects) {
        List<Long> combined = java.util.stream.Stream.concat(photos.stream(), defects.stream())
                .toList();
        if (new HashSet<>(combined).size() != combined.size()) {
            throw new BusinessException(MediaErrorCode.MEDIA_INVALID_FILE);
        }
        return combined;
    }

    private void validateDefectImages(ItemCondition condition, List<Long> defectMediaIds) {
        if (condition == ItemCondition.DEFECTIVE && defectMediaIds.isEmpty()) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_DEFECT_IMAGES_REQUIRED,
                    ItemErrorCode.ITEM_DEFECT_IMAGES_REQUIRED.defaultMessage(),
                    "defectImages",
                    null);
        }
        if (condition != ItemCondition.DEFECTIVE && !defectMediaIds.isEmpty()) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_DEFECT_IMAGES_NOT_ALLOWED,
                    ItemErrorCode.ITEM_DEFECT_IMAGES_NOT_ALLOWED.defaultMessage(),
                    "defectImages",
                    null);
        }
    }

    private Optional<Long> requireOperatingStatus(Long categoryId, OperatingStatus operatingStatus) {
        Optional<Long> attributeId = categoryAttributeService.operatingStatusAttributeId(categoryId);
        if (attributeId.isPresent() && operatingStatus == null) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_OPERATING_STATUS_REQUIRED,
                    ItemErrorCode.ITEM_OPERATING_STATUS_REQUIRED.defaultMessage(),
                    "operatingStatus",
                    null);
        }
        if (attributeId.isEmpty() && operatingStatus != null) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_OPERATING_STATUS_NOT_ALLOWED,
                    ItemErrorCode.ITEM_OPERATING_STATUS_NOT_ALLOWED.defaultMessage(),
                    "operatingStatus",
                    null);
        }
        return attributeId;
    }

    private void saveOperatingStatus(long itemId, long attributeId, OperatingStatus status) {
        jdbcClient
                .sql("INSERT INTO item_attribute_mappings (item_id, attribute_id, value) "
                        + "VALUES (:itemId, :attributeId, :value)")
                .param("itemId", itemId)
                .param("attributeId", attributeId)
                .param("value", status.name())
                .update();
    }

    private void replaceOperatingStatus(
            long itemId, long previousCategoryId, Optional<Long> newAttributeId, OperatingStatus status) {
        categoryAttributeService
                .operatingStatusAttributeId(previousCategoryId)
                .ifPresent(id -> jdbcClient
                        .sql(
                                "DELETE FROM item_attribute_mappings WHERE item_id = :itemId AND attribute_id = :attributeId")
                        .param("itemId", itemId)
                        .param("attributeId", id)
                        .update());
        newAttributeId.ifPresent(id -> saveOperatingStatus(itemId, id, status));
    }

    private List<String> mediaUrls(List<Long> mediaIds) {
        return mediaIds.stream().map(id -> "/api/v1/media/" + id).toList();
    }

    // Lưu cả từ khoá đã khớp để Admin ở hàng chờ duyệt thấy ngay vì sao bài bị gắn cờ.
    private void saveReviewFlag(long itemId, ModerationResult moderation) {
        jdbcClient
                .sql("UPDATE items SET ai_result = jsonb_build_object('flag', 'REVIEW', 'reason', :reason, "
                        + "'matchedTerms', CAST(:matchedTerms AS jsonb)) WHERE id = :itemId")
                .param("reason", moderation.reasonCode())
                .param("matchedTerms", objectMapper.writeValueAsString(moderation.matchedTerms()))
                .param("itemId", itemId)
                .update();
    }

    private void saveMediaMappings(Long itemId, List<Long> mediaIds, String role) {
        short order = 0;
        for (Long mediaId : mediaIds) {
            jdbcClient
                    .sql("INSERT INTO item_media_mappings (item_id, media_id, role, sort_order) "
                            + "VALUES (:itemId, :mediaId, :role, :sortOrder)")
                    .param("itemId", itemId)
                    .param("mediaId", mediaId)
                    .param("role", role)
                    .param("sortOrder", order++)
                    .update();
        }
    }

    private List<String> findImages(Long itemId) {
        return findImages(itemId, "PHOTO");
    }

    private List<String> findImages(Long itemId, String role) {
        if (jdbcClient == null || itemId == null) {
            return List.of();
        }
        return jdbcClient
                .sql("SELECT '/api/v1/media/' || m.id FROM item_media_mappings imm "
                        + "JOIN media_files m ON imm.media_id = m.id "
                        + "WHERE imm.item_id = :itemId AND imm.role = :role "
                        + "AND m.is_active AND NOT m.is_deleted ORDER BY imm.sort_order")
                .param("itemId", itemId)
                .param("role", role)
                .query(String.class)
                .list();
    }

    private List<Long> findMediaIds(long itemId, String role) {
        return jdbcClient
                .sql(
                        "SELECT media_id FROM item_media_mappings WHERE item_id = :itemId AND role = :role ORDER BY sort_order")
                .param("itemId", itemId)
                .param("role", role)
                .query(Long.class)
                .list();
    }

    private List<ItemAttributeValue> findAttributeValues(long itemId) {
        if (jdbcClient == null) {
            return List.of();
        }
        return jdbcClient
                .sql("SELECT attribute_id, value FROM item_attribute_mappings "
                        + "WHERE item_id = :itemId ORDER BY attribute_id")
                .param("itemId", itemId)
                .query((resultSet, rowNumber) ->
                        new ItemAttributeValue(resultSet.getLong("attribute_id"), resultSet.getString("value")))
                .list();
    }

    private OperatingStatus findOperatingStatus(long itemId, long categoryId) {
        return categoryAttributeService
                .operatingStatusAttributeId(categoryId)
                .flatMap(id -> jdbcClient
                        .sql("SELECT value FROM item_attribute_mappings "
                                + "WHERE item_id = :itemId AND attribute_id = :attributeId")
                        .param("itemId", itemId)
                        .param("attributeId", id)
                        .query(String.class)
                        .optional())
                .map(OperatingStatus::valueOf)
                .orElse(null);
    }

    private ItemResponse toResponse(Item item, DisplayWindow displayWindow) {
        return ItemMapper.toResponse(
                item,
                findImages(item.getId()),
                findImages(item.getId(), "DEFECT"),
                findOperatingStatus(item.getId(), item.getCategoryId()),
                displayWindow);
    }

    // Đọc một trang bằng một query hạn hiển thị cho cả trang, thay vì một query mỗi bài.
    private PageResponse<ItemResponse> toPageResponse(Page<Item> items) {
        Map<Long, DisplayWindow> displayWindows = findDisplayWindows(items.getContent());
        return PageResponse.from(items.map(item -> toResponse(item, displayWindows.get(item.getId()))));
    }

    // expire_at, boosted_until thuộc module listingfee nên đọc qua ListingFeeQueryService, không map lại ở Item.
    private Map<Long, DisplayWindow> findDisplayWindows(List<Item> items) {
        List<Long> itemIds = items.stream().map(Item::getId).toList();
        return listingFeeQueryService.findDisplayWindows(itemIds);
    }

    private DisplayWindow findDisplayWindow(long itemId) {
        return listingFeeQueryService.findDisplayWindows(List.of(itemId)).get(itemId);
    }
}
