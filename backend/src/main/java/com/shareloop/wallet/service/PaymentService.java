package com.shareloop.wallet.service;

import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.payment.PaymentGatewayClient;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.TopUpConfirmResponse;
import com.shareloop.wallet.dto.TopUpOrderDetailResponse;
import com.shareloop.wallet.dto.TopUpOrderResponse;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.mapper.WalletMapper;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import com.shareloop.wallet.repository.PaymentOrderSnapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đơn nạp Credit (F04, UC-10): tạo đơn và xác nhận đơn. Việc cộng tiền giao cho {@link CreditService#topUp}, nơi
 * duy nhất được đổi số dư.
 *
 * <p>Tỉ lệ quy đổi (vnd_per_credit) và mức nạp tối thiểu (topup_min_credits) đọc từ ConfigService, không viết cứng.
 */
@Service
@Transactional
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Lý do ghi vào đơn khi người dùng tự hủy trên trang Ví. */
    static final String USER_CANCELLED_REASON = "Bạn đã hủy đơn nạp.";

    /** Lý do ghi vào đơn cũ khi người dùng chọn "Hủy đơn cũ & tạo đơn mới". */
    static final String REPLACED_REASON = "Bạn đã hủy đơn này để tạo đơn nạp mới.";

    /** vnp_ResponseCode 24: khách hủy giao dịch trên trang VNPay. */
    static final String VNPAY_CUSTOMER_CANCELLED = "24";

    private final PaymentOrderRepository paymentOrderRepository;
    private final CreditService creditService;
    private final ConfigService configService;
    private final PaymentGatewayClient paymentGatewayClient;
    private final WalletMapper walletMapper;
    private final PaymentDeadline paymentDeadline;
    private final UserService userService;

    @Autowired
    public PaymentService(
            PaymentOrderRepository paymentOrderRepository,
            CreditService creditService,
            ConfigService configService,
            PaymentGatewayClient paymentGatewayClient,
            WalletMapper walletMapper,
            PaymentDeadline paymentDeadline,
            UserService userService) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.creditService = creditService;
        this.configService = configService;
        this.paymentGatewayClient = paymentGatewayClient;
        this.walletMapper = walletMapper;
        this.paymentDeadline = paymentDeadline;
        this.userService = userService;
    }

    public PaymentService(
            PaymentOrderRepository paymentOrderRepository,
            CreditService creditService,
            ConfigService configService,
            PaymentGatewayClient paymentGatewayClient,
            WalletMapper walletMapper,
            PaymentDeadline paymentDeadline) {
        this(
                paymentOrderRepository,
                creditService,
                configService,
                paymentGatewayClient,
                walletMapper,
                paymentDeadline,
                null);
    }

    /**
     * Tạo đơn nạp PENDING và trả địa chỉ thanh toán (VI-03).
     *
     * <p>Quy tắc: số tiền phải là bội số của vnd_per_credit và quy ra ít nhất topup_min_credits Credit. Đơn lưu
     * lại vnd_per_credit tại thời điểm nạp để đối soát.
     *
     * @param userId người nạp (lấy từ token)
     * @param amountVnd số tiền nạp (VNĐ), đã được DTO kiểm là số dương
     * @return đơn vừa tạo kèm redirectUrl
     * @throws com.shareloop.common.exception.BusinessException WALLET_TOPUP_AMOUNT_INVALID khi dưới mức tối thiểu
     *     hoặc không phải bội số; WALLET_TOPUP_PENDING_EXISTS khi còn đơn đang chờ thanh toán
     */
    public TopUpOrderResponse createTopUpOrder(long userId, long amountVnd) {
        return createTopUpOrder(userId, amountVnd, false);
    }

    /**
     * Tạo đơn nạp với quy tắc mỗi người một đơn đang chờ thanh toán (PENDING còn hạn).
     *
     * <p>Còn đơn đang chờ: replacePending = false thì ném WALLET_TOPUP_PENDING_EXISTS (409) kèm details của đơn đó để FE
     * hỏi người dùng; replacePending = true thì hủy đơn cũ (CANCELLED, UPDATE có điều kiện status = PENDING) rồi tạo đơn
     * mới trong cùng transaction. Khoá dòng users trước (cùng thứ tự với IPN) nên hai lần tạo đơn cùng lúc, hay tạo đơn
     * trong lúc IPN của đơn cũ đang chạy, đều chạy lần lượt. Đơn PENDING đã quá hạn cộng dung sai được chuyển EXPIRED
     * trước khi kiểm.
     *
     * <p>URL thanh toán được lưu vào đơn để "Tiếp tục thanh toán" dùng lại đúng URL đó (cùng vnp_TxnRef,
     * vnp_ExpireDate), không tạo mã giao dịch mới.
     */
    public TopUpOrderResponse createTopUpOrder(long userId, long amountVnd, boolean replacePending) {
        int vndPerCredit = configService.getInt(ConfigKey.VND_PER_CREDIT);
        int minCredits = configService.getInt(ConfigKey.TOPUP_MIN_CREDITS);
        requireValidTopUpAmount(amountVnd, vndPerCredit, minCredits);

        creditService.lockWalletRow(userId);
        expireStalePending(userId);
        PaymentOrder activePending = findActivePending(userId);
        if (activePending != null) {
            if (!replacePending) {
                throw pendingExists(activePending);
            }
            int cancelled = paymentOrderRepository.cancelAllPendingOfUser(userId, REPLACED_REASON);
            log.info("User {} replaced {} pending top-up order(s) with a new one", userId, cancelled);
        }

        int credits = Math.toIntExact(amountVnd / vndPerCredit);
        PaymentOrder order = new PaymentOrder(
                userId, newOrderCode(), amountVnd, credits, vndPerCredit, paymentGatewayClient.provider());
        paymentOrderRepository.save(order);

        // Cùng một mốc expiresAt cho URL VNPay (vnp_ExpireDate) và cho FE đếm ngược.
        Instant expiresAt = expiresAt(order);
        String redirectUrl =
                paymentGatewayClient.buildRedirectUrl(order.getOrderCode(), amountVnd, order.getCreatedAt(), expiresAt);
        order.attachPaymentUrl(redirectUrl);
        return walletMapper.toTopUpOrderResponse(order, redirectUrl, expiresAt, paymentDeadline.now());
    }

    /**
     * Đơn nạp của chính mình, mới nhất trước (GET /api/v1/wallet/topup-orders). status = PENDING chỉ trả đơn còn trong
     * hạn thanh toán (mục "Đơn đang chờ thanh toán"); không truyền status là toàn bộ lịch sử. Đơn PENDING đã quá hạn
     * cộng dung sai được chuyển EXPIRED trước khi đọc. Chỉ trả đơn của userId, không bao giờ của người khác.
     */
    public PageResponse<TopUpOrderDetailResponse> listOwnOrders(
            long userId, PaymentOrderStatus status, int page, int size) {
        expireStalePending(userId);
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        PageRequest pageRequest = PageRequest.of(page, size, newestFirst);
        Page<PaymentOrder> orderPage = status == PaymentOrderStatus.PENDING
                ? paymentOrderRepository.findActivePendingOrders(
                        userId, paymentDeadline.activeCreatedAfter(), pageRequest)
                : paymentOrderRepository.findOwnOrders(userId, status, pageRequest);
        return PageResponse.from(orderPage.map(order -> detail(order, Map.of(), true)));
    }

    private void expireStalePending(long userId) {
        int expired = paymentOrderRepository.expireStalePendingOfUser(userId, paymentDeadline.closedCreatedBefore());
        if (expired > 0) {
            log.info("Expired {} stale pending top-up order(s) of user {}", expired, userId);
        }
    }

    private PaymentOrder findActivePending(long userId) {
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        return paymentOrderRepository
                .findActivePendingOrders(
                        userId, paymentDeadline.activeCreatedAfter(), PageRequest.of(0, 1, newestFirst))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private BusinessException pendingExists(PaymentOrder order) {
        Map<String, Object> details = new HashMap<>();
        details.put("orderCode", order.getOrderCode());
        details.put("amountVnd", order.getAmountVnd());
        details.put("credits", order.getCredits());
        details.put("expiresAt", String.valueOf(expiresAt(order)));
        return new BusinessException(
                WalletErrorCode.WALLET_TOPUP_PENDING_EXISTS,
                WalletErrorCode.WALLET_TOPUP_PENDING_EXISTS.defaultMessage(),
                null,
                details);
    }

    /**
     * Xem một đơn nạp của chính mình (GET /api/v1/wallet/topup-orders/{orderCode}). FE gọi khi người dùng quay về từ
     * cổng thanh toán hoặc tải lại trang Ví để biết đơn đã được cộng chưa và còn hạn thanh toán tới lúc nào.
     *
     * <p>Đơn PENDING đã qua mốc đóng hẳn (expiresAt + dung sai lệch giờ với VNPay, xem PaymentDeadline) thì chuyển
     * EXPIRED ngay khi xem, không chờ PaymentExpiryJob (5 phút một lần). Trong khoảng dung sai đơn vẫn PENDING để IPN
     * hợp lệ tới muộn còn được cộng; FE tự hiện "Đơn đã hết hạn" theo expiresAt và hỏi lại tới khi có kết quả cuối.
     * Không trả tên người dùng và Admin duyệt.
     *
     * @param userId người gọi, lấy từ token
     * @param orderCode mã đơn
     * @return chi tiết đơn kèm expiresAt
     * @throws BusinessException WALLET_PAYMENT_ORDER_NOT_FOUND khi không có đơn hoặc đơn của người khác (không để lộ
     *     đơn đó có tồn tại)
     */
    public TopUpOrderDetailResponse getOwnOrder(long userId, String orderCode) {
        PaymentOrder order = findOwnOrder(userId, orderCode);
        if (order.getStatus() == PaymentOrderStatus.PENDING
                && order.getCreatedAt() != null
                && paymentDeadline.isClosed(order.getCreatedAt())
                && paymentOrderRepository.expireIfPending(order.getId()) == 1) {
            log.info("Top-up order {} expired on read", orderCode);
            order = findOwnOrder(userId, orderCode);
        }
        return detail(order, Map.of(), true);
    }

    private PaymentOrder findOwnOrder(long userId, String orderCode) {
        return paymentOrderRepository
                .findByOrderCode(orderCode)
                .filter(found -> found.getUserId() == userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND));
    }

    /**
     * Người dùng tự hủy đơn nạp đang chờ thanh toán (POST /api/v1/wallet/topup-orders/{orderCode}/cancel). Đơn chuyển
     * FAILED ngay, lý do "Bạn đã hủy đơn nạp." Không đụng ví: đơn PENDING chưa giữ hay cộng Credit nào.
     *
     * <p>Idempotent: đổi bằng một câu UPDATE có điều kiện status = PENDING. Gọi lại khi đơn đã FAILED, hoặc đơn đã
     * SUCCESS/EXPIRED, thì không đổi gì và trả trạng thái hiện tại của đơn.
     *
     * @param userId người gọi, lấy từ token
     * @param orderCode mã đơn
     * @return chi tiết đơn sau khi hủy (hoặc trạng thái hiện tại nếu đơn không còn PENDING)
     * @throws BusinessException WALLET_PAYMENT_ORDER_NOT_FOUND khi không có đơn hoặc đơn của người khác
     */
    public TopUpOrderDetailResponse cancelOwnOrder(long userId, String orderCode) {
        int cancelled = paymentOrderRepository.cancelPending(orderCode, userId, USER_CANCELLED_REASON);
        if (cancelled == 0) {
            log.info("Top-up order {} not cancelled: not PENDING or not owned by user {}", orderCode, userId);
        }
        return getOwnOrder(userId, orderCode);
    }

    /**
     * Xác nhận thanh toán giả lập cho một đơn của chính mình rồi cộng Credit (VI-04, VI-07).
     *
     * <p>Quy tắc: đơn đã SUCCESS thì không cộng lại, vẫn trả SUCCESS cùng ví hiện tại (CreditService.topUp kiểm lại
     * sau khi khoá nên hai lời gọi cùng lúc cũng chỉ cộng một lần).
     *
     * @param userId người xác nhận (lấy từ token), phải là chủ đơn
     * @param orderCode mã đơn
     * @return trạng thái SUCCESS, số Credit của đơn và ví sau khi cộng
     * @throws com.shareloop.common.exception.BusinessException WALLET_PAYMENT_ORDER_NOT_FOUND khi không có đơn hoặc
     *     đơn của người khác; WALLET_PAYMENT_ORDER_NOT_PAYABLE khi đơn FAILED hoặc EXPIRED
     */
    public TopUpConfirmResponse confirmMockPayment(long userId, String orderCode) {
        // Đọc bằng projection (không tạo entity) để lần khoá đơn trong topUp đọc dữ liệu mới từ DB.
        PaymentOrderSnapshot snapshot = paymentOrderRepository
                .findSnapshotByOrderCode(orderCode)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND));
        if (snapshot.getUserId() != userId) {
            throw new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND);
        }

        if (snapshot.getProvider() != PaymentProvider.MOCK) {
            throw new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_PAYABLE);
        }
        WalletResponse wallet = creditService.topUp(userId, snapshot.getId());
        return new TopUpConfirmResponse(orderCode, PaymentOrderStatus.SUCCESS, snapshot.getCredits(), wallet);
    }

    // Hạn thanh toán lấy từ PaymentDeadline (một chỗ tính duy nhất). created_at do JPA Auditing gán lúc persist nên
    // luôn
    // có sau save; chỉ null khi repository bị mock trong unit test.
    private Instant expiresAt(PaymentOrder order) {
        if (order.getCreatedAt() == null) {
            return null;
        }
        return paymentDeadline.expiresAt(order.getCreatedAt());
    }

    // includePaymentUrl: chỉ API của chủ đơn trả URL thanh toán, và chỉ khi đơn còn PENDING trong hạn.
    private TopUpOrderDetailResponse detail(
            PaymentOrder order, Map<Long, UserBrief> briefs, boolean includePaymentUrl) {
        UserBrief owner = briefs.get(order.getUserId());
        UserBrief reviewer = order.getReviewedBy() == null ? null : briefs.get(order.getReviewedBy());
        return new TopUpOrderDetailResponse(
                order.getOrderCode(),
                order.getUserId(),
                order.getAmountVnd(),
                order.getCredits(),
                order.getVndPerCredit(),
                order.getStatus(),
                order.getProvider(),
                order.getCreatedAt(),
                expiresAt(order),
                order.getSubmittedAt(),
                order.getReviewedBy(),
                order.getReviewedAt(),
                order.getReviewNote(),
                owner == null ? null : owner.fullName(),
                owner == null ? null : owner.email(),
                reviewer == null ? null : reviewer.fullName(),
                failureReason(order),
                paymentDeadline.now(),
                includePaymentUrl ? payableUrl(order) : null);
    }

    private String payableUrl(PaymentOrder order) {
        boolean payable = order.getStatus() == PaymentOrderStatus.PENDING
                && order.getCreatedAt() != null
                && !paymentDeadline.isPastExpiresAt(order.getCreatedAt());
        return payable ? order.getPaymentUrl() : null;
    }

    /**
     * Lý do đơn FAILED / CANCELLED cho người dùng đọc. Ghi chú xử lý (tự hủy, hủy để tạo đơn mới, cần hoàn tiền) ưu
     * tiên trước; không có thì suy từ vnp_ResponseCode trong callback_data. Trạng thái khác trả null.
     */
    private static final String PAID_AFTER_CLOSED_MESSAGE =
            "VNPay đã báo thu tiền sau khi đơn hết hạn. Admin sẽ kiểm tra và cộng Credit hoặc hoàn tiền cho bạn.";

    static String failureReason(PaymentOrder order) {
        // Đơn đã đóng nhưng VNPay báo đã thu tiền và Admin chưa đối soát: người dùng đọc câu cố định, không đọc ghi chú
        // nội bộ
        if (order.getPaidAt() != null
                && order.getReviewedAt() == null
                && order.getStatus() != PaymentOrderStatus.PENDING
                && order.getStatus() != PaymentOrderStatus.SUCCESS) {
            return PAID_AFTER_CLOSED_MESSAGE;
        }
        if (order.getStatus() != PaymentOrderStatus.FAILED && order.getStatus() != PaymentOrderStatus.CANCELLED) {
            return null;
        }
        if (order.getReviewNote() != null && !order.getReviewNote().isBlank()) {
            return order.getReviewNote();
        }
        Map<String, String> callback = order.getCallbackData();
        String responseCode = callback == null ? null : callback.get("vnp_ResponseCode");
        if (responseCode == null) {
            return order.getStatus() == PaymentOrderStatus.CANCELLED
                    ? "Đơn nạp đã bị hủy."
                    : "Thanh toán không thành công.";
        }
        if (VNPAY_CUSTOMER_CANCELLED.equals(responseCode)) {
            return "Bạn đã hủy giao dịch tại VNPay.";
        }
        return "VNPay báo thanh toán không thành công (mã " + responseCode + ").";
    }

    private void requireValidTopUpAmount(long amountVnd, int vndPerCredit, int minCredits) {
        long minAmountVnd = (long) minCredits * vndPerCredit;
        boolean belowMinimum = amountVnd < minAmountVnd;
        boolean notMultiple = amountVnd % vndPerCredit != 0;
        if (belowMinimum || notMultiple) {
            throw new BusinessException(WalletErrorCode.WALLET_TOPUP_AMOUNT_INVALID);
        }
    }

    // UUID bỏ dấu "-" còn 32 ký tự, vừa cột order_code VARCHAR(40) và không đoán được.
    private String newOrderCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
