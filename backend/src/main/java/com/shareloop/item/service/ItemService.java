package com.shareloop.item.service;

import com.shareloop.catalog.service.CategoryAttributeService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.CreateItemRequest;
import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.dto.UpdateItemRequest;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.entity.OperatingStatus;
import com.shareloop.item.mapper.ItemMapper;
import com.shareloop.item.repository.ItemRepository;
import com.shareloop.listingfee.service.PostFeeService;
import com.shareloop.media.MediaErrorCode;
import com.shareloop.media.service.MediaService;
import com.shareloop.moderation.ModerationStatus;
import com.shareloop.moderation.service.ContentModerationService;
import com.shareloop.request.service.RequestQueryService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final CategoryAttributeService categoryAttributeService;

    public ItemService(
            ItemRepository itemRepository,
            @Autowired(required = false) JdbcClient jdbcClient,
            MediaService mediaService,
            ConfigService configService,
            RequestQueryService requestQueryService,
            ContentModerationService contentModerationService,
            PostFeeService postFeeService,
            CategoryAttributeService categoryAttributeService) {
        this.itemRepository = itemRepository;
        this.jdbcClient = jdbcClient;
        this.mediaService = mediaService;
        this.configService = configService;
        this.requestQueryService = requestQueryService;
        this.contentModerationService = contentModerationService;
        this.postFeeService = postFeeService;
        this.categoryAttributeService = categoryAttributeService;
    }

    /**
     * Tạo bài đăng mới ở trạng thái PENDING_REVIEW (F07 bước 1).
     */
    @Transactional
    public ItemResponse createItem(Long donorId, CreateItemRequest request) {
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
            jdbcClient
                    .sql("UPDATE items SET ai_result = jsonb_build_object('flag', 'REVIEW', 'reason', :reason) "
                            + "WHERE id = :itemId")
                    .param("reason", moderation.reasonCode())
                    .param("itemId", saved.getId())
                    .update();
        }
        return ItemMapper.toResponse(saved, mediaUrls(mediaIds), mediaUrls(defectMediaIds), request.operatingStatus());
    }

    @Transactional
    public ItemResponse updateItem(long itemId, long userId, UpdateItemRequest request) {
        Item item = requireItem(itemId);
        if (item.getDonorId() != userId) {
            throw new BusinessException(ItemErrorCode.ITEM_NOT_OWNER);
        }
        if (item.getStatus() == ItemStatus.RESERVED
                || item.getStatus() == ItemStatus.TRADED
                || item.getStatus() == ItemStatus.EXPIRED
                || requestQueryService.hasReservedOrHigherRequest(itemId)) {
            throw new BusinessException(ItemErrorCode.ITEM_EDIT_NOT_ALLOWED);
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
        return toResponse(item);
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
            return toResponse(item);
        }

        throw new BusinessException(ItemErrorCode.ITEM_NOT_FOUND);
    }

    /**
     * Xem danh sách bài đăng của chính mình.
     */
    @Transactional(readOnly = true)
    public List<ItemResponse> listMyItems(Long currentUserId) {
        return itemRepository.findByDonorIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> listPublicItems(Long categoryId, Long areaId, String q, int page, int size) {
        Page<Item> items = q == null || q.isBlank()
                ? itemRepository.findVisibleByFilters(
                        ItemStatus.APPROVED,
                        categoryId,
                        areaId,
                        PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                : itemRepository.searchVisibleByFilters(categoryId, areaId, q.trim(), PageRequest.of(page, size));
        Page<ItemResponse> result = items.map(this::toResponse);
        return PageResponse.from(result);
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

    private ItemResponse toResponse(Item item) {
        return ItemMapper.toResponse(
                item,
                findImages(item.getId()),
                findImages(item.getId(), "DEFECT"),
                findOperatingStatus(item.getId(), item.getCategoryId()));
    }
}
