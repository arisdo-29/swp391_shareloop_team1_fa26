package com.shareloop.item.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.ItemCardDto;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only gateway for modules that need item facts without accessing item repositories. */
@Service
@Transactional(readOnly = true)
public class ItemQueryService {

    private final ItemRepository repository;
    private final JdbcClient jdbcClient;

    public ItemQueryService(ItemRepository repository) {
        this(repository, null);
    }

    @Autowired
    public ItemQueryService(ItemRepository repository, @Autowired(required = false) JdbcClient jdbcClient) {
        this.repository = repository;
        this.jdbcClient = jdbcClient;
    }

    public ItemSummary getSummary(long itemId) {
        Item item = repository.findById(itemId).orElseThrow(() -> new BusinessException(ItemErrorCode.ITEM_NOT_FOUND));
        return new ItemSummary(item.getId(), item.getDonorId(), item.getOfferType(), item.getStatus());
    }

    public boolean isApproved(long itemId) {
        Item item = repository.findById(itemId).orElseThrow(() -> new BusinessException(ItemErrorCode.ITEM_NOT_FOUND));
        return item.isActive() && item.getStatus() == ItemStatus.APPROVED;
    }

    public long countApprovedByDonor(long donorId) {
        return repository.countByDonorIdAndStatusAndIsActiveTrue(donorId, ItemStatus.APPROVED);
    }

    public ItemCardDto getItemCard(long itemId) {
        Item item = repository.findById(itemId).orElseThrow(() -> new BusinessException(ItemErrorCode.ITEM_NOT_FOUND));
        String imageUrl = findFirstImage(itemId);
        return new ItemCardDto(
                item.getId(), item.getTitle(), item.getOfferType(), item.getStatus(), imageUrl, item.getDonorId());
    }

    private String findFirstImage(long itemId) {
        if (jdbcClient == null) {
            return null;
        }
        return jdbcClient
                .sql("SELECT '/api/v1/media/' || m.id FROM item_media_mappings imm "
                        + "JOIN media_files m ON imm.media_id = m.id "
                        + "WHERE imm.item_id = :itemId AND imm.role = 'PHOTO' "
                        + "AND m.is_active AND NOT m.is_deleted ORDER BY imm.sort_order LIMIT 1")
                .param("itemId", itemId)
                .query(String.class)
                .optional()
                .orElse(null);
    }
}
