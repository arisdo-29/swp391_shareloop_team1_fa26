package com.shareloop.search.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.search.SearchErrorCode;
import com.shareloop.search.dto.AiQuotaResponse;
import com.shareloop.search.entity.AiQuotaAccount;
import com.shareloop.search.repository.AiQuotaAccountRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.service.CreditService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiQuotaService {
    private final AiQuotaAccountRepository repository;
    private final CreditService creditService;
    private final ConfigService configService;
    private final Clock clock;

    public AiQuotaService(
            AiQuotaAccountRepository repository,
            CreditService creditService,
            ConfigService configService,
            Clock clock) {
        this.repository = repository;
        this.creditService = creditService;
        this.configService = configService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AiQuotaResponse getQuota(long userId) {
        AiQuotaAccount account = requireAccount(userId);
        WalletResponse wallet = creditService.getWallet(userId);
        return snapshot(account, wallet.hasToppedUp(), LocalDate.now(clock));
    }

    /** Check affordability before calling the provider without changing the wallet or free allowance. */
    @Transactional(readOnly = true)
    public void checkAvailable(long userId) {
        AiQuotaAccount account = requireAccount(userId);
        WalletResponse wallet = creditService.getWallet(userId);
        if (remaining(account, wallet.hasToppedUp(), LocalDate.now(clock)) == 0
                && wallet.available() < configService.getInt(ConfigKey.AI_SEARCH_FEE)) {
            throw new BusinessException(WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
        }
    }

    /** Consume only after at least one result exists; wallet and allowance updates share this transaction. */
    @Transactional
    public boolean consume(long userId) {
        creditService.lockWalletRow(userId);
        AiQuotaAccount account = requireAccount(userId);
        boolean toppedUp = creditService.getWallet(userId).hasToppedUp();
        LocalDate today = LocalDate.now(clock);
        if (remaining(account, toppedUp, today) > 0) {
            account.use(today, toppedUp);
            return false;
        }
        creditService.chargeAiSearch(userId);
        return true;
    }

    /** Legacy accounting API retained for wallet tests; AI search uses checkAvailable/consume. */
    @Deprecated
    @Transactional
    public Reservation reserve(long userId) {
        creditService.lockWalletRow(userId);
        AiQuotaAccount account = requireAccount(userId);
        boolean toppedUp = creditService.getWallet(userId).hasToppedUp();
        LocalDate today = LocalDate.now(clock);
        if (remaining(account, toppedUp, today) > 0) {
            account.use(today, toppedUp);
            return new Reservation(today, toppedUp, null);
        }
        return new Reservation(today, toppedUp, creditService.chargeAiSearch(userId));
    }

    @Deprecated
    @Transactional
    public void refund(long userId, Reservation reservation) {
        creditService.lockWalletRow(userId);
        if (reservation.ledgerId() != null) {
            creditService.refundAiSearch(userId, reservation.ledgerId());
        } else {
            requireAccount(userId).refund(reservation.date(), reservation.toppedUp());
        }
    }

    private AiQuotaAccount requireAccount(long userId) {
        return repository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(SearchErrorCode.SEARCH_ACCOUNT_NOT_FOUND));
    }

    private AiQuotaResponse snapshot(AiQuotaAccount account, boolean toppedUp, LocalDate today) {
        return new AiQuotaResponse(
                remaining(account, toppedUp, today), configService.getInt(ConfigKey.AI_SEARCH_FEE), toppedUp);
    }

    private int remaining(AiQuotaAccount account, boolean toppedUp, LocalDate today) {
        int allowance = configService.getInt(toppedUp ? ConfigKey.AI_FREE_PER_DAY : ConfigKey.AI_TRIAL_USES);
        int used = toppedUp && !today.equals(account.getFreeAiDate()) ? 0 : account.getFreeAiUsed();
        return Math.max(0, allowance - used);
    }

    @Deprecated
    public record Reservation(LocalDate date, boolean toppedUp, Long ledgerId) {
        public boolean paid() {
            return ledgerId != null;
        }
    }
}
