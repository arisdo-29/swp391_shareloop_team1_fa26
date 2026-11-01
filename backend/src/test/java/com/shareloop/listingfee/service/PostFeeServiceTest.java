package com.shareloop.listingfee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.shareloop.item.service.ItemService;
import com.shareloop.listingfee.repository.ItemFeeRepository;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.wallet.service.CreditService;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit test số ngày hiển thị theo hạng (BR-D03, FR-29). Cùng package để gọi được hàm package-private. */
@ExtendWith(MockitoExtension.class)
class PostFeeServiceTest {

    private static final long DONOR_ID = 7L;

    @Mock
    ItemFeeRepository itemFeeRepository;

    @Mock
    CreditService creditService;

    @Mock
    ConfigService configService;

    @Mock
    ReputationService reputationService;

    @Mock
    ItemService itemService;

    private PostFeeService postFeeService;

    @BeforeEach
    void setUp() {
        postFeeService = new PostFeeService(
                itemFeeRepository, creditService, configService, reputationService, itemService, Clock.systemUTC());
    }

    @Test
    void displayDaysAfterApproval_whenRankGold_usesGoldDisplayDays() {
        // given
        when(reputationService.getRank(DONOR_ID)).thenReturn(Rank.GOLD);
        when(configService.getInt(ConfigKey.POST_DISPLAY_DAYS_GOLD)).thenReturn(45);

        // when
        int displayDays = postFeeService.displayDaysAfterApproval(DONOR_ID);

        // then
        assertThat(displayDays).isEqualTo(45);
    }

    @Test
    void displayDaysAfterApproval_whenRankDiamond_usesGoldDisplayDays() {
        // given: "từ hạng Vàng trở lên" gồm cả Kim cương
        when(reputationService.getRank(DONOR_ID)).thenReturn(Rank.DIAMOND);
        when(configService.getInt(ConfigKey.POST_DISPLAY_DAYS_GOLD)).thenReturn(45);

        // when
        int displayDays = postFeeService.displayDaysAfterApproval(DONOR_ID);

        // then
        assertThat(displayDays).isEqualTo(45);
    }

    @Test
    void displayDaysAfterApproval_whenRankBronze_usesNormalDisplayDays() {
        // given
        when(reputationService.getRank(DONOR_ID)).thenReturn(Rank.BRONZE);
        when(configService.getInt(ConfigKey.POST_DISPLAY_DAYS)).thenReturn(30);

        // when
        int displayDays = postFeeService.displayDaysAfterApproval(DONOR_ID);

        // then
        assertThat(displayDays).isEqualTo(30);
    }
}
