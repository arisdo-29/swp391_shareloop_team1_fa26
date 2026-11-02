package com.shareloop.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.search.entity.AiQuotaAccount;
import com.shareloop.search.repository.AiQuotaAccountRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.service.CreditService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiQuotaServiceTest {
    private static final long USER_ID = 42L;

    @Test
    void dailyAllowanceResetsAtVietnamMidnight() {
        AiQuotaAccount account = mock(AiQuotaAccount.class);
        when(account.getFreeAiDate()).thenReturn(LocalDate.of(2026, 11, 2));
        when(account.getFreeAiUsed()).thenReturn(5);
        AiQuotaAccountRepository repository = mock(AiQuotaAccountRepository.class);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(account));
        CreditService creditService = mock(CreditService.class);
        when(creditService.getWallet(USER_ID)).thenReturn(new WalletResponse(0, 0, 0, true));
        ConfigService configService = mock(ConfigService.class);
        when(configService.getInt(ConfigKey.AI_FREE_PER_DAY)).thenReturn(5);
        when(configService.getInt(ConfigKey.AI_SEARCH_FEE)).thenReturn(2);

        AiQuotaService beforeMidnight = new AiQuotaService(
                repository,
                creditService,
                configService,
                Clock.fixed(Instant.parse("2026-11-02T16:59:59Z"), ZoneOffset.UTC));
        assertThat(beforeMidnight.getQuota(USER_ID).freeUsesRemaining()).isZero();
        assertThatThrownBy(() -> beforeMidnight.checkAvailable(USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(WalletErrorCode.WALLET_INSUFFICIENT_BALANCE));

        AiQuotaService afterMidnight = new AiQuotaService(
                repository,
                creditService,
                configService,
                Clock.fixed(Instant.parse("2026-11-02T17:00:00Z"), ZoneOffset.UTC));
        assertThat(afterMidnight.getQuota(USER_ID).freeUsesRemaining()).isEqualTo(5);
        afterMidnight.checkAvailable(USER_ID);
        assertThat(afterMidnight.consume(USER_ID)).isFalse();
        verify(account).use(LocalDate.of(2026, 11, 3), true);
        verify(creditService, never()).chargeAiSearch(USER_ID);
    }
}
