package com.shareloop.wallet.service;

import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.CreditTypeTotal;
import com.shareloop.wallet.dto.DailyCashTotal;
import com.shareloop.wallet.dto.FinanceSummaryResponse;
import com.shareloop.wallet.dto.TopUpReconciliation;
import com.shareloop.wallet.dto.WalletHistoryCounts;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.dto.WalletTransactionPage;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import com.shareloop.wallet.entity.CreditLedgerEntry;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.entity.WalletAccount;
import com.shareloop.wallet.mapper.WalletMapper;
import com.shareloop.wallet.repository.CashTotalsView;
import com.shareloop.wallet.repository.CreditLedgerRepository;
import com.shareloop.wallet.repository.CreditTypeTotalView;
import com.shareloop.wallet.repository.PaidOrderView;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import com.shareloop.wallet.repository.UserTopUpTotalView;
import com.shareloop.wallet.repository.WalletAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nơi duy nhất được đổi tiền trong hệ thống (Lộ trình 4.7, 4.8). Mọi hàm đổi số dư khoá dòng {@code users} của
 * người dùng bằng {@code WalletAccountRepository.lockById} (SELECT ... FOR UPDATE) và chạy trong transaction của
 * người gọi (REQUIRED); khi không có transaction ngoài thì tự mở transaction mới.
 *
 * <p>Số tiền thường do người gọi truyền vào; người gọi lấy mức phí từ ConfigService (post_fee, edit_fee, renew_fee,
 * boost_fee). Riêng {@link #chargeAiSearch} tự đọc ai_search_fee từ ConfigService. CreditService không viết cứng con
 * số nào.
 *
 * <p>Người gọi: listingfee (hold, release, chargeHeld), item (spend), search (chargeAiSearch, refundAiSearch),
 * wallet.PaymentService (topUp).
 */
@Service
// @Transactional ở class: mọi hàm public chạy trong một transaction (REQUIRED = tham gia transaction của người
// gọi nếu có, không thì tự mở). Hàm ném RuntimeException thì hoàn tác cả số dư lẫn dòng ledger.
@Transactional
public class CreditService {

    // Mốc thay cho from / to khi người dùng không lọc; để truy vấn luôn có đủ hai đầu khoảng, không cần query riêng.
    // Múi giờ dùng để chia tiền thu theo ngày trong báo cáo, cùng múi giờ AdminFinanceService đổi from/to.
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Instant EARLIEST = Instant.EPOCH;
    private static final Instant LATEST = Instant.parse("9999-12-31T00:00:00Z");

    // Dùng EnumSet vì contains(null) trả false; Set.of(...) sẽ ném NullPointerException khi type là null.
    private static final Set<CreditTxType> HELD_FEE_TYPES = EnumSet.of(CreditTxType.POST_FEE, CreditTxType.EDIT_FEE);
    private static final Set<CreditTxType> SPEND_TYPES =
            EnumSet.of(CreditTxType.RENEW_FEE, CreditTxType.BOOST_FEE, CreditTxType.AI_SEARCH_FEE);

    private final WalletAccountRepository walletAccountRepository;
    private final CreditLedgerRepository creditLedgerRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final WalletMapper walletMapper;
    private final ConfigService configService;
    private final Clock clock;

    public CreditService(
            WalletAccountRepository walletAccountRepository,
            CreditLedgerRepository creditLedgerRepository,
            PaymentOrderRepository paymentOrderRepository,
            WalletMapper walletMapper,
            ConfigService configService,
            Clock clock) {
        this.walletAccountRepository = walletAccountRepository;
        this.creditLedgerRepository = creditLedgerRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.walletMapper = walletMapper;
        this.configService = configService;
        this.clock = clock;
    }

    /**
     * Giữ chỗ Credit khi gửi bài chờ duyệt (C02: giữ khi gửi, trừ khi duyệt lần đầu; F05 bước 3, F07 bước 4).
     *
     * <p>Quy tắc: nếu {@code credit_balance - held_credit >= amount} thì {@code held_credit += amount}; không đổi
     * {@code credit_balance} và không ghi credit_ledger.
     *
     * <p>Transaction: REQUIRED, tham gia transaction của người gọi (ví dụ tạo bài). Khoá: dòng users của userId.
     *
     * @param userId người bị giữ Credit
     * @param amount số Credit giữ, phải lớn hơn 0
     * @param type POST_FEE hoặc EDIT_FEE (dùng cho kiểm tra hợp lệ và log)
     * @param itemId bài liên quan
     * @throws com.shareloop.common.exception.BusinessException WALLET_INSUFFICIENT_BALANCE khi khả dụng không đủ;
     *     WALLET_INVALID_AMOUNT khi amount không dương; WALLET_NOT_FOUND khi không có user
     * @throws IllegalArgumentException khi type không phải POST_FEE hoặc EDIT_FEE (lỗi lập trình, không phải lỗi
     *     nghiệp vụ)
     */
    public void hold(long userId, long amount, CreditTxType type, Long itemId) {
        requireAllowedType(type, HELD_FEE_TYPES, "hold");
        requirePositiveAmount(amount);

        WalletAccount wallet = lockWallet(userId);
        // Không gọi save: entity đang được quản lý trong transaction, Hibernate tự UPDATE khi commit (dirty checking).
        wallet.hold(amount);
    }

    /**
     * Nhả Credit đang giữ khi Admin từ chối hoặc chủ bài rút/xoá bài (F06 bước 4, F05 DELETE).
     *
     * <p>Quy tắc: {@code held_credit -= amount}; {@code credit_balance} giữ nguyên, không ghi credit_ledger.
     *
     * <p>Transaction: REQUIRED, tham gia transaction của người gọi. Khoá: dòng users của userId.
     *
     * @param userId người được nhả
     * @param amount số Credit nhả, phải lớn hơn 0
     * @param type loại phí đang giữ (POST_FEE hoặc EDIT_FEE)
     * @param itemId bài liên quan
     * @throws com.shareloop.common.exception.BusinessException WALLET_HELD_INSUFFICIENT khi held_credit nhỏ hơn
     *     amount; WALLET_INVALID_AMOUNT; WALLET_NOT_FOUND
     * @throws IllegalArgumentException khi type không phải POST_FEE hoặc EDIT_FEE
     */
    public void release(long userId, long amount, CreditTxType type, Long itemId) {
        requireAllowedType(type, HELD_FEE_TYPES, "release");
        requirePositiveAmount(amount);

        WalletAccount wallet = lockWallet(userId);
        wallet.release(amount);
    }

    /**
     * Trừ khoản đang giữ khi Admin duyệt (C02, F06 bước 3).
     *
     * <p>Quy tắc: {@code held_credit -= amount} và {@code credit_balance -= amount} trong một lần, ghi
     * credit_ledger với amount âm, balance_after, item_id và type (POST_FEE hoặc EDIT_FEE).
     *
     * <p>Transaction: REQUIRED, tham gia transaction của người gọi (ReviewService.approve). Khoá: dòng users
     * của userId.
     *
     * @param userId người bị trừ (chủ bài)
     * @param amount số Credit trừ, phải lớn hơn 0
     * @param type POST_FEE hoặc EDIT_FEE
     * @param itemId bài liên quan
     * @throws com.shareloop.common.exception.BusinessException WALLET_HELD_INSUFFICIENT khi held_credit nhỏ hơn
     *     amount; WALLET_INVALID_AMOUNT; WALLET_NOT_FOUND
     * @throws IllegalArgumentException khi type không phải POST_FEE hoặc EDIT_FEE
     */
    public void chargeHeld(long userId, long amount, CreditTxType type, Long itemId) {
        requireAllowedType(type, HELD_FEE_TYPES, "chargeHeld");
        requirePositiveAmount(amount);

        WalletAccount wallet = lockWallet(userId);
        wallet.chargeHeld(amount);
        writeDebitLedger(userId, amount, type, wallet.getCreditBalance(), itemId);
    }

    /**
     * Trừ Credit ngay, không qua giữ chỗ: gia hạn (C16, RENEW_FEE), đẩy bài (C15, BOOST_FEE), AI trả lời
     * (C17, AI_SEARCH_FEE).
     *
     * <p>Quy tắc: nếu {@code credit_balance - held_credit >= amount} thì {@code credit_balance -= amount} và ghi
     * credit_ledger (amount âm, balance_after, item_id nếu có, type). Credit đang giữ cho bài khác không bị dùng.
     *
     * <p>Transaction: REQUIRED, cùng transaction với thay đổi nghiệp vụ của người gọi để lỗi thì hoàn tác cả hai.
     * Khoá: dòng users của userId.
     *
     * @param userId người bị trừ
     * @param amount số Credit trừ, phải lớn hơn 0
     * @param type RENEW_FEE, BOOST_FEE hoặc AI_SEARCH_FEE
     * @param itemId bài liên quan; null khi không gắn bài (AI_SEARCH_FEE)
     * @throws com.shareloop.common.exception.BusinessException WALLET_INSUFFICIENT_BALANCE khi khả dụng không đủ;
     *     WALLET_INVALID_AMOUNT; WALLET_NOT_FOUND
     * @throws IllegalArgumentException khi type không phải RENEW_FEE, BOOST_FEE hoặc AI_SEARCH_FEE
     */
    public void spend(long userId, long amount, CreditTxType type, Long itemId) {
        requireAllowedType(type, SPEND_TYPES, "spend");
        requirePositiveAmount(amount);

        WalletAccount wallet = lockWallet(userId);
        wallet.spend(amount);
        writeDebitLedger(userId, amount, type, wallet.getCreditBalance(), itemId);
    }

    /**
     * Trừ phí một lần AI trợ lý tìm đồ trả lời (C17, AI_SEARCH_FEE). Mức phí đọc từ ConfigKey.AI_SEARCH_FEE; người gọi
     * chỉ truyền userId. Trừ từ phần khả dụng như {@link #spend}.
     *
     * <p>Cách dùng: gọi hàm này trước khi gọi AI và giữ lại id trả về; nếu gọi AI lỗi thì gọi {@link #refundAiSearch}
     * với id đó để hoàn phí.
     *
     * <p>Transaction: REQUIRED. Khoá: dòng users của userId.
     *
     * @param userId người bị trừ (lấy từ token)
     * @return id của dòng credit_ledger vừa ghi (amount âm)
     * @throws com.shareloop.common.exception.BusinessException WALLET_INSUFFICIENT_BALANCE khi khả dụng không đủ;
     *     WALLET_INVALID_AMOUNT khi ai_search_fee cấu hình không dương; WALLET_NOT_FOUND khi không có user
     */
    public long chargeAiSearch(long userId) {
        long fee = configService.getInt(ConfigKey.AI_SEARCH_FEE);
        requirePositiveAmount(fee);

        WalletAccount wallet = lockWallet(userId);
        wallet.spend(fee);
        return writeDebitLedger(userId, fee, CreditTxType.AI_SEARCH_FEE, wallet.getCreditBalance(), null);
    }

    /**
     * Hoàn phí AI khi lần gọi AI đã trừ tiền bị lỗi (C17). Cộng lại đúng số đã trừ ở dòng gốc (không đọc lại cấu
     * hình, vì Admin có thể đã đổi mức phí) và ghi một dòng ledger AI_SEARCH_FEE dương, note "Hoàn phí AI #ledgerId".
     *
     * <p>Quy tắc: dòng gốc phải thuộc userId, có type AI_SEARCH_FEE, amount âm, và chưa từng được hoàn.
     *
     * <p>Transaction: REQUIRED. Khoá dòng users của userId trước khi kiểm "đã hoàn chưa", nên hai lời gọi hoàn cùng
     * lúc cho một dòng gốc chạy lần lượt và lời gọi sau bị chặn.
     *
     * @param userId chủ ví, phải là người đã bị trừ ở dòng gốc
     * @param ledgerId id do {@link #chargeAiSearch} trả về
     * @throws com.shareloop.common.exception.BusinessException WALLET_AI_FEE_NOT_FOUND khi không có dòng gốc, dòng
     *     của người khác hoặc không phải dòng trừ phí AI; WALLET_AI_FEE_ALREADY_REFUNDED khi đã hoàn trước đó;
     *     WALLET_NOT_FOUND khi không có user
     */
    public void refundAiSearch(long userId, long ledgerId) {
        WalletAccount wallet = lockWallet(userId);
        CreditLedgerEntry originalEntry = findAiSearchCharge(userId, ledgerId);

        String note = "Hoàn phí AI #" + ledgerId;
        if (creditLedgerRepository.existsByUserIdAndTypeAndNote(userId, CreditTxType.AI_SEARCH_FEE, note)) {
            throw new BusinessException(WalletErrorCode.WALLET_AI_FEE_ALREADY_REFUNDED);
        }

        // Dòng gốc lưu số âm, đổi dấu để ra số cần cộng lại.
        int refundAmount = -originalEntry.getAmount();
        wallet.refund(refundAmount);
        creditLedgerRepository.save(CreditLedgerEntry.refund(
                userId, refundAmount, CreditTxType.AI_SEARCH_FEE, wallet.getCreditBalance(), note));
    }

    /**
     * Khoá dòng users của userId tới hết transaction của người gọi, không đổi số dư. Dùng khi người gọi phải đọc rồi
     * quyết định dựa trên dữ liệu của chính người dùng đó mà không để luồng khác chen giữa (ví dụ
     * {@code PostFeeService.holdEditFee}: hai lần sửa cùng lúc của một chủ bài phải chạy lần lượt).
     *
     * <p>Transaction: REQUIRED, phải gọi bên trong transaction của người gọi thì khoá mới còn tác dụng. Khoá: dòng
     * users của userId. Các hàm hold, release, chargeHeld gọi sau đó trong cùng transaction khoá lại chính dòng này,
     * không bị chặn.
     *
     * @param userId người dùng cần khoá ví
     * @throws com.shareloop.common.exception.BusinessException WALLET_NOT_FOUND khi không có user
     */
    public void lockWalletRow(long userId) {
        lockWallet(userId);
    }

    /**
     * Chốt đơn nạp đã được cổng xác nhận và cộng Credit (F04 bước 3; FR-14, BR-P10).
     *
     * <p>Quy tắc: đơn PENDING thì chuyển SUCCESS, {@code credit_balance += credits} của đơn,
     * {@code has_topped_up = true}, ghi credit_ledger TOP_UP với payment_order_id và balance_after. Đơn đã SUCCESS
     * thì không cộng nữa, chỉ trả ví hiện tại (gọi lại nhiều lần vẫn an toàn). Số Credit lấy từ
     * {@code payment_orders.credits} (đã tính khi tạo đơn theo ConfigService), hàm này không tự tính.
     *
     * <p>Transaction: REQUIRED. Khoá theo thứ tự dòng users rồi dòng payment_orders, và kiểm trạng thái SAU khi
     * khoá: hai lời gọi cùng lúc cho một đơn thì lời gọi sau chờ, rồi thấy SUCCESS nên không cộng lần hai. Người
     * gọi chỉ gọi hàm này sau khi cổng đã xác nhận thanh toán.
     *
     * @param userId chủ ví, phải là chủ đơn
     * @param paymentOrderId đơn nạp cần chốt
     * @return ví sau khi cộng, hoặc ví hiện tại nếu đơn đã được cộng từ trước
     * @throws com.shareloop.common.exception.BusinessException WALLET_PAYMENT_ORDER_NOT_FOUND khi không có đơn hoặc
     *     đơn của người khác; WALLET_PAYMENT_ORDER_NOT_PAYABLE khi đơn FAILED hoặc EXPIRED; WALLET_NOT_FOUND
     */
    public WalletResponse topUp(long userId, long paymentOrderId) {
        WalletAccount wallet = lockWallet(userId);
        PaymentOrder order = lockPaymentOrder(paymentOrderId);
        requireOrderOwner(order, userId);

        if (order.getProvider() == PaymentProvider.MANUAL) {
            throw new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_PAYABLE);
        }

        return applyTopUp(wallet, order);
    }

    /** Đọc chủ đơn bằng projection rồi khóa users → payment_orders; IPN đã xác minh chữ ký trước khi gọi. */
    public WalletResponse topUpFromGateway(long paymentOrderId) {
        long userId = paymentOrderRepository
                .findUserIdById(paymentOrderId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND));
        WalletAccount wallet = lockWallet(userId);
        PaymentOrder order = lockPaymentOrder(paymentOrderId);
        if (order.getProvider() != PaymentProvider.VNPAY) {
            throw new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_PAYABLE);
        }
        return applyTopUp(wallet, order);
    }

    public WalletResponse topUpManual(long userId, long paymentOrderId) {
        WalletAccount wallet = lockWallet(userId);
        PaymentOrder order = lockPaymentOrder(paymentOrderId);
        requireOrderOwner(order, userId);
        if (order.getProvider() != PaymentProvider.MANUAL || order.getSubmittedAt() == null) {
            throw new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_PAYABLE);
        }
        return applyTopUp(wallet, order);
    }

    private WalletResponse applyTopUp(WalletAccount wallet, PaymentOrder order) {
        long userId = order.getUserId();
        if (order.getStatus() == PaymentOrderStatus.SUCCESS) {
            return walletMapper.toResponse(wallet);
        }
        if (order.getStatus() != PaymentOrderStatus.PENDING) {
            throw new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_PAYABLE);
        }

        order.markSuccess(clock.instant());
        wallet.topUp(order.getCredits());
        creditLedgerRepository.save(
                CreditLedgerEntry.topUp(userId, order.getCredits(), wallet.getCreditBalance(), order.getId()));
        return walletMapper.toResponse(wallet);
    }

    /**
     * Lịch sử ví của chính người dùng, mới nhất trước (UC-11, FR-16, BR-P09).
     *
     * <p>Quy tắc: chỉ các dòng credit_ledger (giao dịch làm đổi số dư thật); giữ chỗ và nhả giữ không ghi sổ cái
     * nên không có ở đây. Lọc theo khoảng [from, to); bỏ trống đầu nào thì không giới hạn đầu đó.
     *
     * <p>Transaction: read-only, không khoá dòng.
     *
     * @param userId chủ ví (lấy từ token)
     * @param from mốc bắt đầu (gồm), có thể null
     * @param to mốc kết thúc (không gồm), có thể null
     * @param page số trang, bắt đầu từ 0
     * @param size số dòng mỗi trang
     * @throws com.shareloop.common.exception.BusinessException WALLET_INVALID_DATE_RANGE khi from không trước to
     */
    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> getTransactions(
            long userId, Instant from, Instant to, int page, int size) {
        Instant rangeStart = from != null ? from : EARLIEST;
        Instant rangeEnd = to != null ? to : LATEST;
        if (!rangeStart.isBefore(rangeEnd)) {
            throw new BusinessException(WalletErrorCode.WALLET_INVALID_DATE_RANGE);
        }

        // Sắp thêm theo id để các dòng cùng created_at vẫn có thứ tự cố định giữa các trang.
        Sort newestFirst = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Page<WalletTransactionResponse> result = creditLedgerRepository
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        userId, rangeStart, rangeEnd, PageRequest.of(page, size, newestFirst))
                .map(walletMapper::toTransactionResponse);
        return PageResponse.from(result);
    }

    /**
     * Lịch sử ví của chính người dùng kèm số dòng theo tab (GET /wallet/transactions, V02).
     *
     * <p>Quy tắc: items và phân trang giống {@link #getTransactions}; thêm counts {all, topUp, spend} đếm trên cả
     * khoảng [from, to) đang lọc, không theo trang, để số trên tab không đổi khi chuyển trang. Spend là khoản trừ
     * Credit (amount âm) trừ Admin điều chỉnh.
     *
     * <p>Transaction: read-only, không khoá dòng.
     *
     * @throws com.shareloop.common.exception.BusinessException WALLET_INVALID_DATE_RANGE khi from không trước to
     */
    @Transactional(readOnly = true)
    public WalletTransactionPage getMyTransactions(long userId, Instant from, Instant to, int page, int size) {
        // Gọi getTransactions trước: hàm đó kiểm from < to nên khoảng sai báo lỗi ngay, không đếm.
        PageResponse<WalletTransactionResponse> pageResponse = getTransactions(userId, from, to, page, size);

        Instant rangeStart = from != null ? from : EARLIEST;
        Instant rangeEnd = to != null ? to : LATEST;
        WalletHistoryCounts counts = new WalletHistoryCounts(
                creditLedgerRepository.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        userId, rangeStart, rangeEnd),
                creditLedgerRepository.countByUserIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        userId, CreditTxType.TOP_UP, rangeStart, rangeEnd),
                creditLedgerRepository.countSpend(userId, CreditTxType.ADMIN_ADJUST, rangeStart, rangeEnd));
        return new WalletTransactionPage(
                pageResponse.items(), pageResponse.page(), pageResponse.size(), pageResponse.totalElements(), counts);
    }

    /**
     * Báo cáo ba sổ tài chính toàn hệ thống trong khoảng [from, to) cho Admin (FR-109, FR-110, BR-P09): tiền mặt thực
     * thu (đơn nạp SUCCESS theo paid_at), Credit cộng vào và Credit trừ ra tách theo type, và dòng đối chiếu tiền mặt
     * quy ra Credit với tổng TOP_UP. Giữ chỗ và nhả giữ không ghi sổ cái nên không có trong báo cáo.
     *
     * <p>Transaction: read-only, không khoá dòng; mọi phép cộng chạy bằng SUM/GROUP BY trong DB.
     *
     * @param from mốc bắt đầu (gồm), có thể null
     * @param to mốc kết thúc (không gồm), có thể null
     * @throws com.shareloop.common.exception.BusinessException WALLET_INVALID_DATE_RANGE khi from không trước to
     */
    @Transactional(readOnly = true)
    public FinanceSummaryResponse getFinanceSummary(Instant from, Instant to) {
        Instant rangeStart = from != null ? from : EARLIEST;
        Instant rangeEnd = to != null ? to : LATEST;
        if (!rangeStart.isBefore(rangeEnd)) {
            throw new BusinessException(WalletErrorCode.WALLET_INVALID_DATE_RANGE);
        }

        CashTotalsView cash =
                paymentOrderRepository.sumByStatusAndPaidAt(PaymentOrderStatus.SUCCESS, rangeStart, rangeEnd);
        List<CreditTypeTotal> creditIn =
                toCreditTypeTotals(creditLedgerRepository.sumCreditInByType(rangeStart, rangeEnd));
        List<CreditTypeTotal> creditOut =
                toCreditTypeTotals(creditLedgerRepository.sumCreditOutByType(rangeStart, rangeEnd));
        TopUpReconciliation reconciliation =
                reconcileTopUp(cash.getCredits(), amountOfType(creditIn, CreditTxType.TOP_UP));
        List<DailyCashTotal> cashByDay =
                groupCashByDay(paymentOrderRepository.findPaidOrders(PaymentOrderStatus.SUCCESS, rangeStart, rangeEnd));

        return new FinanceSummaryResponse(
                from,
                to,
                cash.getOrderCount(),
                cash.getAmountVnd(),
                sumAmounts(creditIn),
                creditIn,
                sumAmounts(creditOut),
                creditOut,
                reconciliation,
                cashByDay);
    }

    /**
     * Đọc ví cho GET /wallet và để module khác kiểm số dư (F04).
     *
     * <p>Transaction: read-only, không khoá dòng (chỉ đọc, không dùng để quyết định đổi tiền).
     *
     * @param userId chủ ví
     * @return số dư, số đang giữ, số khả dụng, đã từng nạp hay chưa
     * @throws com.shareloop.common.exception.BusinessException WALLET_NOT_FOUND khi không có user
     */
    // readOnly = true ghi đè @Transactional ở class: báo cho DB và Hibernate biết hàm này chỉ đọc.
    @Transactional(readOnly = true)
    public WalletResponse getWallet(long userId) {
        return walletAccountRepository
                .findById(userId)
                .map(walletMapper::toResponse)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
    }

    /**
     * Ví của nhiều người dùng trong một lần truy vấn, dùng cho danh sách tra cứu của Admin (FR-112). Người dùng không
     * có ví (không tồn tại hoặc đã xoá mềm) không có trong kết quả.
     *
     * <p>Transaction: read-only, không khoá dòng.
     */
    @Transactional(readOnly = true)
    public Map<Long, WalletResponse> getWallets(Collection<Long> userIds) {
        Map<Long, WalletResponse> wallets = new HashMap<>();
        if (userIds.isEmpty()) {
            return wallets;
        }
        for (WalletAccount account : walletAccountRepository.findAllById(userIds)) {
            wallets.put(account.getId(), walletMapper.toResponse(account));
        }
        return wallets;
    }

    /**
     * Tổng Credit đã nạp thành công (đơn SUCCESS) của từng người dùng. Người chưa nạp lần nào không có trong kết quả,
     * người gọi coi là 0.
     *
     * <p>Transaction: read-only, không khoá dòng.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> getToppedUpCredits(Collection<Long> userIds) {
        Map<Long, Long> totals = new HashMap<>();
        if (userIds.isEmpty()) {
            return totals;
        }
        for (UserTopUpTotalView total : paymentOrderRepository.sumCreditsByUser(PaymentOrderStatus.SUCCESS, userIds)) {
            totals.put(total.getUserId(), total.getCredits());
        }
        return totals;
    }

    // type sai là lỗi của người gọi (lập trình viên), không phải tình huống nghiệp vụ nên không dùng BusinessException.
    private void requireAllowedType(CreditTxType type, Set<CreditTxType> allowedTypes, String operation) {
        if (!allowedTypes.contains(type)) {
            throw new IllegalArgumentException(operation + " chỉ nhận " + allowedTypes + " nhưng nhận " + type);
        }
    }

    private void requirePositiveAmount(long amount) {
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }
    }

    private WalletAccount lockWallet(long userId) {
        return walletAccountRepository
                .lockById(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
    }

    private PaymentOrder lockPaymentOrder(long paymentOrderId) {
        return paymentOrderRepository
                .lockById(paymentOrderId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND));
    }

    // Đơn của người khác báo "không tìm thấy" như đơn không tồn tại, để không lộ là mã đơn đó có thật.
    private void requireOrderOwner(PaymentOrder order, long userId) {
        if (order.getUserId() != userId) {
            throw new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND);
        }
    }

    // Dòng không có, của người khác, hay không phải dòng trừ phí AI đều báo cùng một lỗi, để không lộ id đó có thật.
    private CreditLedgerEntry findAiSearchCharge(long userId, long ledgerId) {
        CreditLedgerEntry entry = creditLedgerRepository
                .findById(ledgerId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_AI_FEE_NOT_FOUND));
        boolean isOwnAiCharge =
                entry.getUserId() == userId && entry.getType() == CreditTxType.AI_SEARCH_FEE && entry.getAmount() < 0;
        if (!isOwnAiCharge) {
            throw new BusinessException(WalletErrorCode.WALLET_AI_FEE_NOT_FOUND);
        }
        return entry;
    }

    private List<CreditTypeTotal> toCreditTypeTotals(List<CreditTypeTotalView> views) {
        return views.stream().map(walletMapper::toCreditTypeTotal).toList();
    }

    private long sumAmounts(List<CreditTypeTotal> totals) {
        long sum = 0;
        for (CreditTypeTotal total : totals) {
            sum += total.amount();
        }
        return sum;
    }

    // Type không có dòng nào trong khoảng thì không xuất hiện trong kết quả GROUP BY, nghĩa là tổng bằng 0.
    private long amountOfType(List<CreditTypeTotal> totals, CreditTxType type) {
        for (CreditTypeTotal total : totals) {
            if (total.type() == type) {
                return total.amount();
            }
        }
        return 0;
    }

    // Gom các đơn đã thanh toán theo ngày lịch Việt Nam (paid_at lưu UTC nên 23h UTC đã là ngày hôm sau ở Việt Nam).
    // TreeMap giữ ngày cũ đứng trước; ngày không có đơn nào thì không xuất hiện.
    private List<DailyCashTotal> groupCashByDay(List<PaidOrderView> paidOrders) {
        Map<LocalDate, long[]> totalsByDay = new TreeMap<>();
        for (PaidOrderView paidOrder : paidOrders) {
            LocalDate day = paidOrder.getPaidAt().atZone(VIETNAM_ZONE).toLocalDate();
            long[] totals = totalsByDay.computeIfAbsent(day, key -> new long[2]);
            totals[0] += paidOrder.getAmountVnd();
            totals[1] += 1;
        }

        List<DailyCashTotal> result = new ArrayList<>();
        for (Map.Entry<LocalDate, long[]> entry : totalsByDay.entrySet()) {
            result.add(new DailyCashTotal(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }
        return result;
    }

    // BR-P09: tiền mặt thực thu quy ra Credit phải bằng tổng Credit TOP_UP; lệch là dấu hiệu cộng thiếu hoặc cộng thừa.
    private TopUpReconciliation reconcileTopUp(long cashCredits, long topUpCredits) {
        long difference = cashCredits - topUpCredits;
        return new TopUpReconciliation(cashCredits, topUpCredits, difference, difference == 0);
    }

    // Cột credit_ledger.amount là INT; toIntExact ném ArithmeticException (và rollback) thay vì cắt số âm thầm.
    // Trả id của dòng vừa ghi: id do DB sinh (IDENTITY) nên save đã INSERT ngay và có id.
    private long writeDebitLedger(long userId, long amount, CreditTxType type, long balanceAfter, Long itemId) {
        int debitAmount = -Math.toIntExact(amount);
        CreditLedgerEntry entry =
                creditLedgerRepository.save(new CreditLedgerEntry(userId, debitAmount, type, balanceAfter, itemId));
        return entry.getId();
    }
}
