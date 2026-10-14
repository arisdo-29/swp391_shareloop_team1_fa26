package com.shareloop.wallet.service;

import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import com.shareloop.wallet.entity.CreditLedgerEntry;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.WalletAccount;
import com.shareloop.wallet.mapper.WalletMapper;
import com.shareloop.wallet.repository.CreditLedgerRepository;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import com.shareloop.wallet.repository.WalletAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
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
 * <p>Mọi số tiền do người gọi truyền vào; người gọi lấy mức phí từ ConfigService (post_fee, edit_fee, renew_fee,
 * boost_fee, ai_search_fee), CreditService không viết cứng con số nào.
 *
 * <p>Người gọi: listingfee (hold, release, chargeHeld), item/search (spend), wallet.PaymentService (topUp).
 */
@Service
// @Transactional ở class: mọi hàm public chạy trong một transaction (REQUIRED = tham gia transaction của người
// gọi nếu có, không thì tự mở). Hàm ném RuntimeException thì hoàn tác cả số dư lẫn dòng ledger.
@Transactional
public class CreditService {

    // Mốc thay cho from / to khi người dùng không lọc; để truy vấn luôn có đủ hai đầu khoảng, không cần query riêng.
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
    private final Clock clock;

    public CreditService(
            WalletAccountRepository walletAccountRepository,
            CreditLedgerRepository creditLedgerRepository,
            PaymentOrderRepository paymentOrderRepository,
            WalletMapper walletMapper,
            Clock clock) {
        this.walletAccountRepository = walletAccountRepository;
        this.creditLedgerRepository = creditLedgerRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.walletMapper = walletMapper;
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

    // Cột credit_ledger.amount là INT; toIntExact ném ArithmeticException (và rollback) thay vì cắt số âm thầm.
    private void writeDebitLedger(long userId, long amount, CreditTxType type, long balanceAfter, Long itemId) {
        int debitAmount = -Math.toIntExact(amount);
        creditLedgerRepository.save(new CreditLedgerEntry(userId, debitAmount, type, balanceAfter, itemId));
    }
}
