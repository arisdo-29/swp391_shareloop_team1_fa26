package com.shareloop.item.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.AdminItemBase;
import com.shareloop.item.dto.AdminItemQuery;
import com.shareloop.item.dto.ItemCardDto;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.repository.ItemRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        return new ItemSummary(item.getId(), item.getDonorId(), item.getOfferType(), item.getStatus(), item.isActive());
    }

    /** Id mọi bài của một chủ bài, dùng để tìm giao dịch của người đó khi khoá tài khoản. */
    public List<Long> findItemIdsByDonor(long donorId) {
        return repository.findByDonorIdOrderByCreatedAtDesc(donorId).stream()
                .map(Item::getId)
                .toList();
    }

    public boolean isApproved(long itemId) {
        Item item = repository.findById(itemId).orElseThrow(() -> new BusinessException(ItemErrorCode.ITEM_NOT_FOUND));
        return item.isActive() && item.getStatus() == ItemStatus.APPROVED;
    }

    /** Danh mục hoặc khu vực của bài đang bị tắt (B3): bài không nhận yêu cầu mới. */
    public boolean hasDisabledCatalog(long itemId) {
        return !repository.isInEnabledCatalog(itemId);
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
     * Tên bài đăng theo id, dùng cho trang nhật ký Admin (AD-05, TB-03). Cố ý không lọc is_deleted để bài đã xoá vẫn hiện
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

    /** Tiêu đề, giá và khu vực công khai của món được đề nghị, kể cả bài đã xoá mềm. */
    public Optional<OfferedItemDetails> findOfferedItemDetails(long itemId) {
        if (jdbcClient == null) {
            return Optional.empty();
        }
        return jdbcClient
                .sql("SELECT title, reference_price, area_id FROM items WHERE id = :itemId")
                .param("itemId", itemId)
                .query(OfferedItemDetails.class)
                .optional();
    }

    public record OfferedItemDetails(String title, Long referencePrice, Long areaId) {}

    // JdbcClient tự map cột id, title vào record theo tên thành phần.

    /**
     * Danh sách bài ở mọi trạng thái cho trang Quản lý bài đăng của Admin, thứ tự theo pageable. Ảnh đầu và ngày duyệt
     * lấy riêng cho cả trang; phần phí, chủ bài, yêu cầu do module admin bổ sung.
     */
    public Page<AdminItemBase> searchForAdmin(AdminItemQuery query, Pageable pageable) {
        boolean hasKeyword = query.keyword() != null && !query.keyword().isBlank();
        String pattern = hasKeyword ? "%" + query.keyword().trim().toLowerCase() + "%" : null;
        Page<Item> items = repository.searchForAdmin(
                query.status(),
                query.active() != null,
                Boolean.TRUE.equals(query.active()),
                query.offerType(),
                query.categoryId() == null,
                query.categoryId() == null ? 0L : query.categoryId(),
                query.ownerId() == null,
                query.ownerId() == null ? 0L : query.ownerId(),
                pattern,
                nonEmpty(hasKeyword ? query.keywordOwnerIds() : null),
                query.from(),
                query.to(),
                query.boostedIds() != null,
                nonEmpty(query.boostedIds()),
                pageable);
        List<Long> ids = items.getContent().stream().map(Item::getId).toList();
        Map<Long, Instant> approvedAts = findApprovedAts(ids);
        return items.map(item -> toAdminBase(item, approvedAts.get(item.getId()), findFirstImage(item.getId())));
    }

    /**
     * Một bài ở mọi trạng thái cho trang chi tiết Admin.
     *
     * @throws BusinessException ITEM_NOT_FOUND khi không có bài hoặc đã xoá
     */
    public AdminItemBase getAdminBase(long itemId) {
        Item item = repository.findById(itemId).orElseThrow(() -> new BusinessException(ItemErrorCode.ITEM_NOT_FOUND));
        return toAdminBase(item, findApprovedAts(List.of(itemId)).get(itemId), findFirstImage(itemId));
    }

    private AdminItemBase toAdminBase(Item item, Instant approvedAt, String imageUrl) {
        return new AdminItemBase(
                item.getId(),
                item.getTitle(),
                item.getDonorId(),
                item.getCategoryId(),
                item.getOfferType(),
                item.getStatus(),
                item.isActive(),
                item.isHiddenByAdmin(),
                item.getCreatedAt(),
                approvedAt,
                imageUrl);
    }

    // IN không được rỗng nên danh sách rỗng hoặc null đổi thành một id không khớp bài nào.
    private static List<Long> nonEmpty(List<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of(-1L) : ids;
    }

    // approved_at thuộc nhóm cột của module review nên đọc bằng JdbcClient, không map thêm vào entity Item.
    private Map<Long, Instant> findApprovedAts(Collection<Long> ids) {
        Map<Long, Instant> approvedAts = new HashMap<>();
        if (jdbcClient == null || ids.isEmpty()) {
            return approvedAts;
        }
        jdbcClient
                .sql("SELECT id, approved_at FROM items WHERE id IN (:ids) AND approved_at IS NOT NULL")
                .param("ids", ids)
                .query((resultSet, rowNumber) -> {
                    approvedAts.put(
                            resultSet.getLong("id"),
                            resultSet.getTimestamp("approved_at").toInstant());
                    return null;
                })
                .list();
        return approvedAts;
    }

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
