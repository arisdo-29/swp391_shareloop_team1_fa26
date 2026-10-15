package com.shareloop.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.repository.ItemRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItemQueryServiceTest {
    @Mock
    ItemRepository repository;

    @InjectMocks
    ItemQueryService service;

    @Test
    void exposesSummaryAndApprovedState() {
        Item item = new Item(10L, 1L, 1L, OfferType.SWAP, "Bàn", "Mô tả", "Ghế", ItemCondition.GOOD, null, null);
        ReflectionTestUtils.setField(item, "id", 7L);
        item.approve();
        when(repository.findById(7L)).thenReturn(Optional.of(item));

        assertThat(service.getSummary(7L).id()).isEqualTo(7L);
        assertThat(service.getSummary(7L).donorId()).isEqualTo(10L);
        assertThat(service.getSummary(7L).offerType()).isEqualTo(OfferType.SWAP);
        assertThat(service.getSummary(7L).status()).isEqualTo(ItemStatus.APPROVED);
        assertThat(service.isApproved(7L)).isTrue();
        item.deactivate();
        assertThat(service.isApproved(7L)).isFalse();
    }

    @Test
    void missingItemThrowsBusinessError() {
        when(repository.findById(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getSummary(8L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_NOT_FOUND);
        assertThatThrownBy(() -> service.isApproved(8L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    void countsOnlyVisibleApprovedItems() {
        when(repository.countByDonorIdAndStatusAndIsActiveTrue(10L, ItemStatus.APPROVED))
                .thenReturn(3L);
        assertThat(service.countApprovedByDonor(10L)).isEqualTo(3L);
    }
}
