package com.shareloop.listingfee.service;

import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.service.ItemService;
import com.shareloop.listingfee.entity.ItemFee;
import com.shareloop.listingfee.repository.ItemFeeRepository;
import com.shareloop.listingfee.repository.ItemFeeSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hết hạn bài đăng (FR-29, BR-D03; ERD mục 6 "Hết hạn bài"): bài APPROVED có expire_at đã qua thì chuyển EXPIRED. Chỉ
 * đọc cột expire_at của listingfee; đổi status bắt buộc qua {@code ItemService.markExpired}.
 *
 * <p>Người gọi: {@code ItemExpiryJob} (mỗi giờ) và test.
 */
@Service
public class ItemExpiryService {

    private static final Logger log = LoggerFactory.getLogger(ItemExpiryService.class);

    // ERD mục 6: xử lý theo lô nhỏ. Đây là tham số kỹ thuật (bao nhiêu id mỗi lần đọc), không phải con số nghiệp vụ,
    // nên không đặt trong ConfigService.
    private static final int BATCH_SIZE = 100;

    private final ItemFeeRepository itemFeeRepository;
    private final ItemService itemService;
    private final TransactionTemplate transactionTemplate;

    public ItemExpiryService(
            ItemFeeRepository itemFeeRepository, ItemService itemService, TransactionTemplate transactionTemplate) {
        this.itemFeeRepository = itemFeeRepository;
        this.itemService = itemService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Chuyển mọi bài APPROVED có expire_at trước {@code now} sang EXPIRED (FR-29, BR-D03). Bài RESERVED,
     * PENDING_REVIEW hay đã EXPIRED không được chọn; chạy lại nhiều lần không có tác dụng kép.
     *
     * <p>Transaction: hàm này không mở transaction; mỗi bài chạy trong một transaction riêng, nên một bài lỗi (ghi log
     * WARN) chỉ hoàn tác chính nó và không chặn các bài còn lại. Không ném lỗi nghiệp vụ.
     *
     * @param now mốc so với expire_at (job truyền giờ hiện tại, test truyền mốc tự chọn)
     * @return số bài đã chuyển sang EXPIRED trong lượt này
     */
    public int expireOverdueItems(Instant now) {
        int expiredCount = 0;
        long lastSeenId = 0;
        List<Long> batch = itemFeeRepository.findExpiredApprovedItemIds(now, lastSeenId, BATCH_SIZE);
        while (!batch.isEmpty()) {
            for (Long itemId : batch) {
                if (expireInOwnTransaction(itemId, now)) {
                    expiredCount++;
                }
            }
            lastSeenId = batch.get(batch.size() - 1);
            batch = itemFeeRepository.findExpiredApprovedItemIds(now, lastSeenId, BATCH_SIZE);
        }
        return expiredCount;
    }

    // TransactionTemplate: mở transaction, chạy lambda, commit; lambda ném lỗi thì rollback và ném tiếp. Dùng thay
    // @Transactional vì gọi một hàm @Transactional trong cùng lớp sẽ không qua proxy nên không có transaction.
    private boolean expireInOwnTransaction(long itemId, Instant now) {
        try {
            Boolean expired = transactionTemplate.execute(status -> expireIfStillOverdue(itemId, now));
            return Boolean.TRUE.equals(expired);
        } catch (RuntimeException exception) {
            // Bắt mọi lỗi để một bài hỏng không dừng cả lượt; bài này vẫn APPROVED nên lượt sau sẽ thử lại.
            log.warn("Item expiry failed for item {}, will retry next run", itemId, exception);
            return false;
        }
    }

    // Khoá dòng items rồi mới kiểm lại: giữa lúc đọc danh sách id và lúc này, bài có thể vừa được gia hạn
    // (RenewService cũng khoá dòng này), được giữ chỗ hoặc bị gỡ. Không còn đủ điều kiện thì bỏ qua, không phải lỗi.
    private boolean expireIfStillOverdue(long itemId, Instant now) {
        Optional<ItemFee> lockedItemFee = itemFeeRepository.lockById(itemId);
        if (lockedItemFee.isEmpty()) {
            return false;
        }
        Instant expireAt = lockedItemFee.get().getExpireAt();
        if (expireAt == null || !expireAt.isBefore(now)) {
            return false;
        }
        // Đọc status sau khi khoá nên thấy dữ liệu mới nhất; ItemFee không map cột status (thuộc module item).
        Optional<ItemFeeSnapshot> snapshot = itemFeeRepository.findFeeSnapshotById(itemId);
        if (snapshot.isEmpty() || snapshot.get().getStatus() != ItemStatus.APPROVED) {
            return false;
        }
        itemService.markExpired(itemId);
        return true;
    }
}
