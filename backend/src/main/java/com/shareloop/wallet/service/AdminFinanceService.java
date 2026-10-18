package com.shareloop.wallet.service;

import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.wallet.FinanceErrorCode;
import com.shareloop.wallet.dto.FinanceSummaryResponse;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service phục vụ báo cáo tài chính và tra cứu lịch sử giao dịch người dùng cho Admin (FR-109, FR-110, FR-112).
 * Mọi số liệu và biến động Credit được ủy thác qua CreditService.
 */
@Service
public class AdminFinanceService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CreditService creditService;

    public AdminFinanceService(CreditService creditService) {
        this.creditService = creditService;
    }

    /**
     * Báo cáo tài chính ba sổ và đối chiếu TopUp (FR-109, FR-110, BR-P09).
     */
    @Transactional(readOnly = true)
    public FinanceSummaryResponse getReport(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(FinanceErrorCode.INVALID_DATE_RANGE);
        }

        Instant fromInstant = from != null ? from.atStartOfDay(VIETNAM_ZONE).toInstant() : null;
        Instant toInstant =
                to != null ? to.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant() : null;

        return creditService.getFinanceSummary(fromInstant, toInstant);
    }

    /**
     * Tra cứu lịch sử ví Credit của một người dùng (FR-112).
     */
    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> getUserHistory(
            Long userId, LocalDate from, LocalDate to, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        // Kiểm tra người dùng / ví có tồn tại không. Nếu không, ném WALLET_NOT_FOUND (404)
        creditService.getWallet(userId);

        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(FinanceErrorCode.INVALID_DATE_RANGE);
        }

        Instant fromInstant = from != null ? from.atStartOfDay(VIETNAM_ZONE).toInstant() : null;
        Instant toInstant =
                to != null ? to.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant() : null;

        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);

        return creditService.getTransactions(userId, fromInstant, toInstant, pageNumber, pageSize);
    }
}
