package com.shareloop.item.service;

import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.CreateItemRequest;
import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.mapper.ItemMapper;
import com.shareloop.item.repository.ItemRepository;
import com.shareloop.media.service.MediaService;
import java.util.List;
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

    public ItemService(
            ItemRepository itemRepository,
            @Autowired(required = false) JdbcClient jdbcClient,
            MediaService mediaService) {
        this.itemRepository = itemRepository;
        this.jdbcClient = jdbcClient;
        this.mediaService = mediaService;
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

        List<Long> mediaIds = parseMediaIds(request.images());
        mediaService.attach(mediaIds, donorId);
        Item saved = itemRepository.save(item);
        saveMediaMappings(saved.getId(), mediaIds);
        return ItemMapper.toResponse(saved, mediaUrls(mediaIds));
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
