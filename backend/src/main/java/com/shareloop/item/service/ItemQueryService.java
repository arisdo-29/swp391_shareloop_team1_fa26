package com.shareloop.item.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.ItemCardDto;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.repository.ItemRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Tên bài đăng theo id, dùng cho trang nhật ký Admin (FR-115). Cố ý không lọc is_deleted để bài đã xoá vẫn hiện
     * tên trong nhật ký. Id không tồn tại sẽ không có trong map.
     */
    public Map<Long, String> findTitles(Collection<Long> ids) {
        Map<Long, String> titles = new HashMap<>();
        if (jdbcClient == null || ids == null || ids.isEmpty()) {
            return titles;
        }
        // Dùng JdbcClient thay vì repository vì entity Item có @SQLRestriction ẩn bài đã xoá mềm.
        List<ItemTitle> rows = jdbcClient
                .sql("SELECT id, title FROM items WHERE id IN (:ids)")
                .param("ids", ids)
                .query(ItemTitle.class)
                .list();
        for (ItemTitle row : rows) {
            titles.put(row.id(), row.title());
        }
        return titles;
    }

    // JdbcClient tự map cột id, title vào record theo tên thành phần.
    private record ItemTitle(Long id, String title) {}

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
