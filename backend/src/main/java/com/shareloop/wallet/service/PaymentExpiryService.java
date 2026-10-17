package com.shareloop.wallet.service;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hết hạn đơn nạp (FR-18): đơn PENDING quá payment_order_expire_minutes mà cổng không phản hồi thì chuyển EXPIRED. Đơn
 * EXPIRED không còn được cộng Credit (CreditService.topUp từ chối với WALLET_PAYMENT_ORDER_NOT_PAYABLE).
 *
 * <p>Người gọi: {@code PaymentExpiryJob} (mỗi 5 phút) và test.
 */
@Service
public class PaymentExpiryService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final ConfigService configService;

    public PaymentExpiryService(PaymentOrderRepository paymentOrderRepository, ConfigService configService) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.configService = configService;
    }

    /**
     * Chuyển mọi đơn PENDING có created_at trước {@code now - payment_order_expire_minutes} sang EXPIRED (FR-18). Đơn
     * SUCCESS, FAILED hay đã EXPIRED không bị đụng tới; chạy lại nhiều lần không có tác dụng kép. Không ném lỗi nghiệp
     * vụ.
     *
     * <p>Transaction: một câu UPDATE hàng loạt trong một transaction (không cần xử lý từng đơn như ItemExpiryService,
     * vì đổi status đơn nạp không kéo theo việc gì khác).
     *
     * @param now mốc hiện tại (job truyền giờ của Clock, test truyền mốc tự chọn)
     * @return số đơn đã chuyển sang EXPIRED trong lượt này
     */
    @Transactional
    public int expireStaleOrders(Instant now) {
        int expireMinutes = configService.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES);
        Instant cutoff = now.minus(expireMinutes, ChronoUnit.MINUTES);
        return paymentOrderRepository.updateStatusByStatusAndCreatedAtBefore(
                PaymentOrderStatus.PENDING, PaymentOrderStatus.EXPIRED, cutoff);
    }
}
