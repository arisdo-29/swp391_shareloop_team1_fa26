package com.shareloop.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.wallet.FinanceErrorCode;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.CreditTypeTotal;
import com.shareloop.wallet.dto.FinanceSummaryResponse;
import com.shareloop.wallet.dto.TopUpReconciliation;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import com.shareloop.wallet.entity.CreditTxType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminFinanceServiceTest {

    private final CreditService creditService = mock(CreditService.class);
    private AdminFinanceService adminFinanceService;

    @BeforeEach
    void setUp() {
        adminFinanceService = new AdminFinanceService(creditService);
    }

    @Test
    @DisplayName("getReport: số liệu khớp hoàn toàn truy vấn tổng hợp của CreditService")
    void getReport_matchesCreditServiceSummary() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 17);

        TopUpReconciliation reconciliation = new TopUpReconciliation(100L, 100L, 0L, true);
        FinanceSummaryResponse expectedSummary = new FinanceSummaryResponse(
                Instant.parse("2026-09-30T17:00:00Z"),
                Instant.parse("2026-10-17T17:00:00Z"),
                3L,
                100000L,
                107L,
                List.of(new CreditTypeTotal(CreditTxType.TOP_UP, 100L, 3L)),
                22L,
                List.of(new CreditTypeTotal(CreditTxType.POST_FEE, 10L, 2L)),
                reconciliation);

        when(creditService.getFinanceSummary(any(Instant.class), any(Instant.class)))
                .thenReturn(expectedSummary);

        FinanceSummaryResponse result = adminFinanceService.getReport(from, to);

        assertThat(result).isNotNull();
        assertThat(result.cashOrderCount()).isEqualTo(3L);
        assertThat(result.cashAmountVnd()).isEqualTo(100000L);
        assertThat(result.creditInTotal()).isEqualTo(107L);
        assertThat(result.creditOutTotal()).isEqualTo(22L);
        assertThat(result.reconciliation().matched()).isTrue();
        assertThat(result.reconciliation().cashCredits()).isEqualTo(100L);
        assertThat(result.reconciliation().topUpCredits()).isEqualTo(100L);

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(creditService).getFinanceSummary(fromCaptor.capture(), toCaptor.capture());

        assertThat(fromCaptor.getValue()).isBefore(toCaptor.getValue());
    }

    @Test
    @DisplayName("getReport: from sau to ném INVALID_DATE_RANGE")
    void getReport_fromAfterTo_throwsInvalidDateRange() {
        LocalDate from = LocalDate.of(2026, 10, 20);
        LocalDate to = LocalDate.of(2026, 10, 10);

        assertThatThrownBy(() -> adminFinanceService.getReport(from, to))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(FinanceErrorCode.INVALID_DATE_RANGE));

        verify(creditService, never()).getFinanceSummary(any(), any());
    }

    @Test
    @DisplayName("getUserHistory: kiểm tra ví tồn tại và gọi getTransactions đúng phân trang")
    void getUserHistory_validUser_returnsTransactions() {
        Long userId = 42L;
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 17);

        WalletResponse wallet = new WalletResponse(50L, 10L, 40L, true);
        when(creditService.getWallet(userId)).thenReturn(wallet);

        WalletTransactionResponse txn =
                new WalletTransactionResponse(1L, CreditTxType.TOP_UP, 50, 50L, null, 1L, null, Instant.now());
        PageResponse<WalletTransactionResponse> pageResponse = new PageResponse<>(List.of(txn), 0, 20, 1L);

        when(creditService.getTransactions(eq(userId), any(Instant.class), any(Instant.class), eq(0), eq(20)))
                .thenReturn(pageResponse);

        PageResponse<WalletTransactionResponse> result = adminFinanceService.getUserHistory(userId, from, to, 0, 20);

        assertThat(result).isNotNull();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).amount()).isEqualTo(50L);
        assertThat(result.totalElements()).isEqualTo(1L);

        verify(creditService).getWallet(userId);
        verify(creditService).getTransactions(eq(userId), any(Instant.class), any(Instant.class), eq(0), eq(20));
    }

    @Test
    @DisplayName("getUserHistory: userId không tồn tại ném WALLET_NOT_FOUND")
    void getUserHistory_userNotFound_throwsWalletNotFound() {
        Long userId = 999L;
        when(creditService.getWallet(userId)).thenThrow(new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        assertThatThrownBy(() -> adminFinanceService.getUserHistory(userId, null, null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(WalletErrorCode.WALLET_NOT_FOUND));

        verify(creditService, never()).getTransactions(any(Long.class), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("getUserHistory: from sau to ném INVALID_DATE_RANGE")
    void getUserHistory_fromAfterTo_throwsInvalidDateRange() {
        Long userId = 42L;
        LocalDate from = LocalDate.of(2026, 10, 25);
        LocalDate to = LocalDate.of(2026, 10, 15);

        when(creditService.getWallet(userId)).thenReturn(new WalletResponse(10L, 0L, 10L, false));

        assertThatThrownBy(() -> adminFinanceService.getUserHistory(userId, from, to, 0, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(FinanceErrorCode.INVALID_DATE_RANGE));
    }
}
