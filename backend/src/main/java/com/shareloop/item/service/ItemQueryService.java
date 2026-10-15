package com.shareloop.item.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only gateway for modules that need item facts without accessing item repositories. */
@Service
@Transactional(readOnly = true)
public class ItemQueryService {

    private final ItemRepository repository;

    public ItemQueryService(ItemRepository repository) {
        this.repository = repository;
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
}
