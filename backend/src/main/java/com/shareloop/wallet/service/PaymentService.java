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
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    private final PaymentOrderRepository paymentOrderRepository;
    private final CreditService creditService;
    private final ConfigService configService;
    private final PaymentGatewayClient paymentGatewayClient;
    private final WalletMapper walletMapper;
    private final UserService userService;

    @Autowired
    public PaymentService(
            PaymentOrderRepository paymentOrderRepository,
            CreditService creditService,
            ConfigService configService,
            PaymentGatewayClient paymentGatewayClient,
            WalletMapper walletMapper,
            UserService userService) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.creditService = creditService;
        this.configService = configService;
        this.paymentGatewayClient = paymentGatewayClient;
        this.walletMapper = walletMapper;
        this.userService = userService;
    }

    public PaymentService(
            PaymentOrderRepository paymentOrderRepository,
            CreditService creditService,
            ConfigService configService,
            PaymentGatewayClient paymentGatewayClient,
            WalletMapper walletMapper) {
        this(paymentOrderRepository, creditService, configService, paymentGatewayClient, walletMapper, null);
    }

    /**
     * Tạo đơn nạp PENDING và trả địa chỉ thanh toán (FR-11, FR-12, BR-P02).
     *
     * <p>Quy tắc: số tiền phải là bội số của vnd_per_credit và quy ra ít nhất topup_min_credits Credit. Đơn lưu
     * lại vnd_per_credit tại thời điểm nạp để đối soát.
     *
     * @param userId người nạp (lấy từ token)
     * @param amountVnd số tiền nạp (VNĐ), đã được DTO kiểm là số dương
     * @return đơn vừa tạo kèm redirectUrl
     * @throws com.shareloop.common.exception.BusinessException WALLET_TOPUP_AMOUNT_INVALID khi dưới mức tối thiểu
     *     hoặc không phải bội số
     */
    public TopUpOrderResponse createTopUpOrder(long userId, long amountVnd) {
        int vndPerCredit = configService.getInt(ConfigKey.VND_PER_CREDIT);
        int minCredits = configService.getInt(ConfigKey.TOPUP_MIN_CREDITS);
        requireValidTopUpAmount(amountVnd, vndPerCredit, minCredits);

        int credits = Math.toIntExact(amountVnd / vndPerCredit);
        PaymentOrder order = new PaymentOrder(
                userId, newOrderCode(), amountVnd, credits, vndPerCredit, paymentGatewayClient.provider());
        paymentOrderRepository.save(order);

        String redirectUrl = paymentGatewayClient.buildRedirectUrl(order.getOrderCode(), amountVnd);
        return walletMapper.toTopUpOrderResponse(order, redirectUrl);
    }

    /**
     * Xác nhận thanh toán giả lập cho một đơn của chính mình rồi cộng Credit (FR-13, FR-14).
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

    /**
     * Danh sách đơn nạp cho Admin, đơn mới tạo nằm trên cùng. provider null nghĩa là mọi cổng thanh toán (VNPAY, MOCK,
     * MANUAL), nên Admin xem được cả đơn VNPay; status null nghĩa là mọi trạng thái.
     * Kèm tên chủ đơn và tên Admin duyệt (nếu có).
     */
    @Transactional(readOnly = true)
    public PageResponse<TopUpOrderDetailResponse> listOrders(
            PaymentProvider provider, PaymentOrderStatus status, boolean submittedOnly, int page, int size) {
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        Page<PaymentOrder> orderPage = paymentOrderRepository.findOrders(
                provider, status, submittedOnly, PageRequest.of(page, size, newestFirst));
        Map<Long, UserBrief> briefs = findBriefs(orderPage.getContent());
        return PageResponse.from(orderPage.map(order -> detail(order, briefs)));
    }

    private Instant expiresAt(PaymentOrder order) {
        return order.getCreatedAt()
                .plus(configService.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES), ChronoUnit.MINUTES);
    }

    private Map<Long, UserBrief> findBriefs(List<PaymentOrder> orderList) {
        if (userService == null) {
            return Map.of();
        }
        Set<Long> userIds = new HashSet<>();
        for (PaymentOrder order : orderList) {
            userIds.add(order.getUserId());
            if (order.getReviewedBy() != null) {
                userIds.add(order.getReviewedBy());
            }
        }
        return userService.findBriefs(userIds);
    }

    private TopUpOrderDetailResponse detail(PaymentOrder order, Map<Long, UserBrief> briefs) {
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
                reviewer == null ? null : reviewer.fullName());
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
