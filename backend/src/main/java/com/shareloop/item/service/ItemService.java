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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý bài đăng: tạo mới, xem chi tiết, xem danh sách bài của tôi.
 */
@Service
public class ItemService {

    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
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
        return ItemMapper.toResponse(saved);
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
            return ItemMapper.toResponse(item);
        }

        throw new BusinessException(ItemErrorCode.ITEM_NOT_FOUND);
    }

    /**
     * Xem danh sách bài đăng của chính mình.
     */
    @Transactional(readOnly = true)
    public List<ItemResponse> listMyItems(Long currentUserId) {
        return itemRepository.findByDonorIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(ItemMapper::toResponse)
                .toList();
    }
}
