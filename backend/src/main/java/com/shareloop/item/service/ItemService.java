package com.shareloop.item.service;

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

    public ItemService(
            ItemRepository itemRepository,
            @Autowired(required = false) JdbcClient jdbcClient,
            MediaService mediaService,
            ConfigService configService,
            RequestQueryService requestQueryService,
            ContentModerationService contentModerationService,
            PostFeeService postFeeService) {
        this.itemRepository = itemRepository;
        this.jdbcClient = jdbcClient;
        this.mediaService = mediaService;
        this.configService = configService;
        this.requestQueryService = requestQueryService;
        this.contentModerationService = contentModerationService;
        this.postFeeService = postFeeService;
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
        mediaService.attach(mediaIds, donorId);
        Item saved = itemRepository.save(item);
        saveMediaMappings(saved.getId(), mediaIds);
        return ItemMapper.toResponse(saved, mediaUrls(mediaIds));
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
        postFeeService.quoteEdit(itemId, userId);
        postFeeService.holdEditFee(itemId, userId);
        if (requestQueryService.hasReservedOrHigherRequest(itemId)) {
            throw new BusinessException(ItemErrorCode.ITEM_EDIT_NOT_ALLOWED);
        }

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
        if (newMediaIds != null) {
            replaceMediaMappings(itemId, userId, newMediaIds);
        }
        jdbcClient
                .sql("UPDATE items SET ai_risk = 'UNCHECKED', ai_result = NULL WHERE id = :itemId")
                .param("itemId", itemId)
                .update();
        return ItemMapper.toResponse(item, findImages(itemId));
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
            return ItemMapper.toResponse(item, findImages(id));
        }

        throw new BusinessException(ItemErrorCode.ITEM_NOT_FOUND);
    }

    /**
     * Xem danh sách bài đăng của chính mình.
     */
    @Transactional(readOnly = true)
    public List<ItemResponse> listMyItems(Long currentUserId) {
        return itemRepository.findByDonorIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(item -> ItemMapper.toResponse(item, findImages(item.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> listPublicItems(Long categoryId, Long areaId, int page, int size) {
        Page<ItemResponse> result = itemRepository
                .findVisibleByFilters(
                        ItemStatus.APPROVED,
                        categoryId,
                        areaId,
                        PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .map(item -> ItemMapper.toResponse(item, findImages(item.getId())));
        return PageResponse.from(result);
    }

    /**
     * Giữ chỗ bài đăng khi chủ bài chọn người nhận (BR-D01, Issue #38).
     */
    @Transactional
    public void reserve(Long itemId) {
        Item item =
                itemRepository.findById(itemId).orElseThrow(() -> new BusinessException(ItemErrorCode.ITEM_NOT_FOUND));
        item.reserve();
    }

    /**
     * Chuyển trạng thái bài đăng sang TRADED khi Request hoàn tất giao dịch (COMPLETED).
     */
    @Transactional
    public void markTraded(long itemId) {
        Item item =
                itemRepository.findById(itemId).orElseThrow(() -> new BusinessException(ItemErrorCode.ITEM_NOT_FOUND));
        item.markTraded();
    }

    /** Temporary gateway; implemented with the item lifecycle in issue #74. */
    public void release(long itemId) {
        throw unsupported("release");
    }

    /** Temporary gateways; implemented with item lifecycle and removal in issue #91. */
    public void hide(long itemId) {
        throw unsupported("hide");
    }

    public void unhide(long itemId) {
        throw unsupported("unhide");
    }

    public void remove(long itemId) {
        throw unsupported("remove");
    }

    public void hideAllOfUser(long userId) {
        throw unsupported("hideAllOfUser");
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

    private UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException("ItemService." + operation + " is not implemented yet");
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

    private void replaceMediaMappings(long itemId, long userId, List<Long> newMediaIds) {
        List<Long> oldMediaIds = jdbcClient
                .sql("SELECT media_id FROM item_media_mappings WHERE item_id = :itemId AND role = 'PHOTO'")
                .param("itemId", itemId)
                .query(Long.class)
                .list();
        List<Long> added =
                newMediaIds.stream().filter(id -> !oldMediaIds.contains(id)).toList();
        List<Long> removed =
                oldMediaIds.stream().filter(id -> !newMediaIds.contains(id)).toList();
        mediaService.attach(added, userId);
        jdbcClient
                .sql("DELETE FROM item_media_mappings WHERE item_id = :itemId AND role = 'PHOTO'")
                .param("itemId", itemId)
                .update();
        saveMediaMappings(itemId, newMediaIds);
        mediaService.detach(removed, userId);
    }

    private List<String> mediaUrls(List<Long> mediaIds) {
        return mediaIds.stream().map(id -> "/api/v1/media/" + id).toList();
    }

    private void saveMediaMappings(Long itemId, List<Long> mediaIds) {
        short order = 0;
        for (Long mediaId : mediaIds) {
            jdbcClient
                    .sql("INSERT INTO item_media_mappings (item_id, media_id, role, sort_order) "
                            + "VALUES (:itemId, :mediaId, 'PHOTO', :sortOrder)")
                    .param("itemId", itemId)
                    .param("mediaId", mediaId)
                    .param("sortOrder", order++)
                    .update();
        }
    }

    private List<String> findImages(Long itemId) {
        if (jdbcClient == null || itemId == null) {
            return List.of();
        }
        return jdbcClient
                .sql("SELECT '/api/v1/media/' || m.id FROM item_media_mappings imm "
                        + "JOIN media_files m ON imm.media_id = m.id "
                        + "WHERE imm.item_id = :itemId AND m.is_active AND NOT m.is_deleted ORDER BY imm.sort_order")
                .param("itemId", itemId)
                .query(String.class)
                .list();
    }
}
