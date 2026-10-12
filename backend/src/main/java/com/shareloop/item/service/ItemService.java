package com.shareloop.item.service;

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
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý bài đăng: tạo mới, xem chi tiết, xem danh sách bài của tôi.
 */
@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final JdbcClient jdbcClient;

    public ItemService(ItemRepository itemRepository, @Autowired(required = false) JdbcClient jdbcClient) {
        this.itemRepository = itemRepository;
        this.jdbcClient = jdbcClient;
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

        Item saved = itemRepository.save(item);
        List<String> savedImages = saveMediaMappings(saved.getId(), donorId, request.images());
        return ItemMapper.toResponse(saved, savedImages);
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
        if (item.getStatus() == ItemStatus.APPROVED || isOwner) {
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

    private List<String> saveMediaMappings(Long itemId, Long donorId, List<String> images) {
        if (images == null || images.isEmpty() || jdbcClient == null) {
            return images != null ? images : List.of();
        }
        List<String> savedImages = new ArrayList<>();
        short order = 0;
        for (String url : images) {
            if (url == null || url.isBlank()) {
                continue;
            }
            String cleanUrl = url.trim();
            String storageKey = cleanUrl.length() > 300 ? cleanUrl.substring(0, 300) : cleanUrl;

            Long mediaId = jdbcClient
                    .sql("SELECT id FROM media_files WHERE storage_key = :key")
                    .param("key", storageKey)
                    .query(Long.class)
                    .optional()
                    .orElseGet(() -> jdbcClient
                            .sql(
                                    "INSERT INTO media_files (storage_key, mime_type, size_bytes, visibility, status, created_by, updated_by) "
                                            + "VALUES (:key, 'image/jpeg', 0, 'PUBLIC', 'ATTACHED', :uid, :uid) RETURNING id")
                            .param("key", storageKey)
                            .param("uid", donorId)
                            .query(Long.class)
                            .single());

            jdbcClient
                    .sql(
                            "INSERT INTO item_media_mappings (item_id, media_id, role, sort_order) "
                                    + "VALUES (:itemId, :mediaId, 'PHOTO', :sortOrder) ON CONFLICT (item_id, media_id) DO NOTHING")
                    .param("itemId", itemId)
                    .param("mediaId", mediaId)
                    .param("sortOrder", order++)
                    .update();

            savedImages.add(cleanUrl);
        }
        return savedImages;
    }

    private List<String> findImages(Long itemId) {
        if (jdbcClient == null || itemId == null) {
            return List.of();
        }
        return jdbcClient
                .sql("SELECT m.storage_key FROM item_media_mappings imm "
                        + "JOIN media_files m ON imm.media_id = m.id "
                        + "WHERE imm.item_id = :itemId ORDER BY imm.sort_order")
                .param("itemId", itemId)
                .query(String.class)
                .list();
    }
}
