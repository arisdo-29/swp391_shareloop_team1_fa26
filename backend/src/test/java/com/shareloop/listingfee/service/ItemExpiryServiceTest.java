package com.shareloop.listingfee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.service.ItemService;
import com.shareloop.listingfee.entity.ItemFee;
import com.shareloop.listingfee.repository.ItemFeeRepository;
import com.shareloop.listingfee.repository.ItemFeeSnapshot;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit test nhánh lỗi của job hết hạn bài: một bài lỗi không làm hỏng cả lượt. Các nhánh còn lại (chọn bài, chạy
 * lại) kiểm trên DB thật ở ItemExpiryServiceIT.
 */
@ExtendWith(MockitoExtension.class)
class ItemExpiryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");

    @Mock
    ItemFeeRepository itemFeeRepository;

    @Mock
    ItemService itemService;

    @Test
    void expireOverdueItems_whenOneItemFails_stillExpiresTheOthers() {
        // given: hai bài quá hạn; bài 1 bị ItemService từ chối chuyển trạng thái
        // TransactionTemplate thật với transaction manager giả: vẫn chạy lambda, rollback khi lambda ném lỗi.
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        ItemExpiryService itemExpiryService =
                new ItemExpiryService(itemFeeRepository, itemService, transactionTemplate);
        when(itemFeeRepository.findExpiredApprovedItemIds(NOW, 0L, 100)).thenReturn(List.of(1L, 2L));
        when(itemFeeRepository.findExpiredApprovedItemIds(NOW, 2L, 100)).thenReturn(List.of());
        ItemFee overdueItemFee = mock(ItemFee.class);
        when(overdueItemFee.getExpireAt()).thenReturn(NOW.minus(1, ChronoUnit.DAYS));
        when(itemFeeRepository.lockById(anyLong())).thenReturn(Optional.of(overdueItemFee));
        ItemFeeSnapshot approvedSnapshot = mock(ItemFeeSnapshot.class);
        when(approvedSnapshot.getStatus()).thenReturn(ItemStatus.APPROVED);
        when(itemFeeRepository.findFeeSnapshotById(anyLong())).thenReturn(Optional.of(approvedSnapshot));
        doThrow(new BusinessException(ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION))
                .when(itemService)
                .markExpired(1L);

        // when
        int expiredCount = itemExpiryService.expireOverdueItems(NOW);

        // then: bài 1 bỏ qua (chỉ log), bài 2 vẫn được chuyển
        assertThat(expiredCount).isEqualTo(1);
        verify(itemService).markExpired(2L);
    }
}
