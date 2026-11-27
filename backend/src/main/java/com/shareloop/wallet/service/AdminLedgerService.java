package com.shareloop.wallet.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.AdminLedgerPage;
import com.shareloop.wallet.dto.AdminLedgerRow;
import com.shareloop.wallet.entity.CreditLedgerEntry;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.mapper.LedgerCsvWriter;
import com.shareloop.wallet.mapper.LedgerExcelWriter;
import com.shareloop.wallet.repository.CreditLedgerRepository;
import com.shareloop.wallet.repository.OrderCodeView;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lịch sử mọi biến động Credit của toàn hệ thống cho Admin đối soát (sổ cái credit_ledger). Chỉ đọc; tiền chỉ đổi
 * trong {@link CreditService}.
 */
@Service
@Transactional(readOnly = true)
public class AdminLedgerService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant EARLIEST = Instant.EPOCH;
    private static final Instant LATEST = Instant.parse("9999-12-31T00:00:00Z");
    private static final int EXPORT_MAX_ROWS = 10_000;

    private final CreditLedgerRepository creditLedgerRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final UserService userService;
    private final ItemQueryService itemQueryService;
    private final LedgerCsvWriter csvWriter;
    private final LedgerExcelWriter excelWriter;

    public AdminLedgerService(
            CreditLedgerRepository creditLedgerRepository,
            PaymentOrderRepository paymentOrderRepository,
            UserService userService,
            ItemQueryService itemQueryService,
            LedgerCsvWriter csvWriter,
            LedgerExcelWriter excelWriter) {
        this.creditLedgerRepository = creditLedgerRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.userService = userService;
        this.itemQueryService = itemQueryService;
        this.csvWriter = csvWriter;
        this.excelWriter = excelWriter;
    }

    /**
     * Một trang sổ cái, mới nhất trước, kèm tổng vào và tổng ra của cả khoảng lọc. Mọi bộ lọc đều tuỳ chọn. keyword
     * khớp tên hoặc email người dùng, ghi chú hoặc mã đơn nạp. from và to tính theo ngày giờ Việt Nam, to tính trọn
     * ngày.
     *
     * @throws BusinessException WALLET_INVALID_DATE_RANGE khi from sau to
     */
    public AdminLedgerPage list(
            CreditTxType type, Long userId, String keyword, LocalDate from, LocalDate to, int page, int size) {
        Filter filter = buildFilter(type, userId, keyword, from, to);
        Pageable pageable = PageRequest.of(page, size, newestFirst());
        Page<CreditLedgerEntry> entries = find(filter, pageable);
        List<AdminLedgerRow> rows = toRows(entries.getContent());
        return new AdminLedgerPage(
                rows,
                entries.getNumber(),
                entries.getSize(),
                entries.getTotalElements(),
                sumIn(filter),
                sumOut(filter));
    }

    /** File CSV theo đúng bộ lọc đang chọn, tối đa 10000 dòng mới nhất. */
    public byte[] exportCsv(CreditTxType type, Long userId, String keyword, LocalDate from, LocalDate to) {
        return csvWriter.write(findExportRows(type, userId, keyword, from, to));
    }

    /** File Excel (.xlsx) theo đúng bộ lọc đang chọn, tối đa 10000 dòng mới nhất. */
    public byte[] exportExcel(CreditTxType type, Long userId, String keyword, LocalDate from, LocalDate to) {
        return excelWriter.write(findExportRows(type, userId, keyword, from, to), type, from, to);
    }

    private List<AdminLedgerRow> findExportRows(
            CreditTxType type, Long userId, String keyword, LocalDate from, LocalDate to) {
        Filter filter = buildFilter(type, userId, keyword, from, to);
        Page<CreditLedgerEntry> entries = find(filter, PageRequest.of(0, EXPORT_MAX_ROWS, newestFirst()));
        return toRows(entries.getContent());
    }

    // Gom các giá trị lọc đã chuẩn hoá một lần để ba truy vấn (trang, tổng vào, tổng ra) dùng chung đúng một bộ.
    private Filter buildFilter(CreditTxType type, Long userId, String keyword, LocalDate from, LocalDate to) {
        Instant fromInstant =
                from == null ? EARLIEST : from.atStartOfDay(VIETNAM_ZONE).toInstant();
        Instant toInstant =
                to == null ? LATEST : to.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant();
        if (!fromInstant.isBefore(toInstant)) {
            throw new BusinessException(WalletErrorCode.WALLET_INVALID_DATE_RANGE);
        }
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        String pattern = hasKeyword ? "%" + keyword.trim().toLowerCase() + "%" : null;
        // IN không được rỗng nên luôn có ít nhất một phần tử; id -1 không khớp người nào.
        List<Long> userIds = new ArrayList<>(hasKeyword ? userService.findIdsByKeyword(keyword) : List.of());
        if (userIds.isEmpty()) {
            userIds.add(-1L);
        }
        return new Filter(type, userId == null, userId == null ? 0L : userId, pattern, userIds, fromInstant, toInstant);
    }

    private Page<CreditLedgerEntry> find(Filter filter, Pageable pageable) {
        return creditLedgerRepository.findForAdmin(
                filter.type(),
                filter.allUsers(),
                filter.userId(),
                filter.keywordPattern(),
                filter.userIds(),
                filter.from(),
                filter.to(),
                pageable);
    }

    private long sumIn(Filter filter) {
        return creditLedgerRepository.sumInForAdmin(
                filter.type(),
                filter.allUsers(),
                filter.userId(),
                filter.keywordPattern(),
                filter.userIds(),
                filter.from(),
                filter.to());
    }

    private long sumOut(Filter filter) {
        return creditLedgerRepository.sumOutForAdmin(
                filter.type(),
                filter.allUsers(),
                filter.userId(),
                filter.keywordPattern(),
                filter.userIds(),
                filter.from(),
                filter.to());
    }

    private static Sort newestFirst() {
        return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }

    // Tra tên người, tiêu đề bài và mã đơn cho cả trang bằng ba truy vấn, không tra từng dòng.
    private List<AdminLedgerRow> toRows(List<CreditLedgerEntry> entries) {
        Set<Long> userIds = new HashSet<>();
        Set<Long> itemIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        for (CreditLedgerEntry entry : entries) {
            userIds.add(entry.getUserId());
            if (entry.getCreatedBy() != null) {
                userIds.add(entry.getCreatedBy());
            }
            if (entry.getItemId() != null) {
                itemIds.add(entry.getItemId());
            }
            if (entry.getPaymentOrderId() != null) {
                orderIds.add(entry.getPaymentOrderId());
            }
        }
        Map<Long, UserBrief> briefs = userService.findBriefs(userIds);
        Map<Long, String> titles = itemQueryService.findTitles(itemIds);
        Map<Long, String> orderCodes = findOrderCodes(orderIds);

        List<AdminLedgerRow> rows = new ArrayList<>();
        for (CreditLedgerEntry entry : entries) {
            rows.add(toRow(entry, briefs, titles, orderCodes));
        }
        return rows;
    }

    private Map<Long, String> findOrderCodes(Set<Long> orderIds) {
        Map<Long, String> codes = new HashMap<>();
        if (orderIds.isEmpty()) {
            return codes;
        }
        for (OrderCodeView view : paymentOrderRepository.findCodesByIds(orderIds)) {
            codes.put(view.getId(), view.getOrderCode());
        }
        return codes;
    }

    private static AdminLedgerRow toRow(
            CreditLedgerEntry entry,
            Map<Long, UserBrief> briefs,
            Map<Long, String> titles,
            Map<Long, String> orderCodes) {
        UserBrief owner = briefs.get(entry.getUserId());
        // created_by được JPA Auditing gán cho mọi dòng (là chính người dùng với các khoản phí), nên chỉ hiện người
        // thực hiện
        // ở dòng Admin điều chỉnh.
        boolean adjustedByAdmin = entry.getType() == CreditTxType.ADMIN_ADJUST && entry.getCreatedBy() != null;
        UserBrief actor = adjustedByAdmin ? briefs.get(entry.getCreatedBy()) : null;
        return new AdminLedgerRow(
                entry.getId(),
                entry.getCreatedAt(),
                entry.getUserId(),
                owner == null ? null : owner.fullName(),
                owner == null ? null : owner.email(),
                entry.getType(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getItemId(),
                entry.getItemId() == null ? null : titles.get(entry.getItemId()),
                entry.getPaymentOrderId() == null ? null : orderCodes.get(entry.getPaymentOrderId()),
                entry.getNote(),
                actor == null ? null : actor.fullName());
    }

    // Bộ giá trị lọc đã chuẩn hoá, truyền nguyên cho các truy vấn.
    private record Filter(
            CreditTxType type,
            boolean allUsers,
            long userId,
            String keywordPattern,
            List<Long> userIds,
            Instant from,
            Instant to) {}
}
