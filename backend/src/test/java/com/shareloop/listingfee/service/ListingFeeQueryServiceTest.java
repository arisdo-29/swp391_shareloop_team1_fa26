package com.shareloop.listingfee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.shareloop.listingfee.dto.DisplayWindow;
import com.shareloop.listingfee.repository.ItemFeeRepository;
import com.shareloop.setting.service.ConfigService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListingFeeQueryServiceTest {

    @Mock
    ItemFeeRepository itemFeeRepository;

    @Mock
    ConfigService configService;

    @InjectMocks
    ListingFeeQueryService listingFeeQueryService;

    @Test
    void findDisplayWindows_whenNoItemIds_returnsEmptyMapWithoutQuery() {
        // given: trang không có bài nào
        List<Long> itemIds = List.of();

        // when
        Map<Long, DisplayWindow> displayWindows = listingFeeQueryService.findDisplayWindows(itemIds);

        // then: không gửi query "IN ()" rỗng xuống DB
        assertThat(displayWindows).isEmpty();
        verifyNoInteractions(itemFeeRepository);
    }

    @Test
    void findCurrentlyBoostedItemIdsDelegatesReadToListingFeeRepository() {
        Instant now = Instant.parse("2026-10-26T00:00:00Z");

        listingFeeQueryService.findCurrentlyBoostedItemIds(now);

        verify(itemFeeRepository).findCurrentlyBoostedItemIds(now);
    }
}
