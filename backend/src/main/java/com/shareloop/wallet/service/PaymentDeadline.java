package com.shareloop.wallet.service;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Một chỗ duy nhất tính hạn thanh toán của đơn nạp, dùng chung cho URL VNPay (vnp_ExpireDate), API trả FE (expiresAt),
 * kiểm IPN muộn và tự hết hạn khi xem đơn. Thời lượng chỉ đọc từ ConfigKey payment_order_expire_minutes.
 *
 * <ul>
 *   <li>{@link #expiresAt}: created_at + payment_order_expire_minutes, làm tròn xuống giây vì vnp_ExpireDate chỉ có tới
 *       giây (yyyyMMddHHmmss). Nhờ vậy đồng hồ trên trang Ví và trang VNPay hết cùng một giây.
 *   <li>{@link #closesAt}: expiresAt + app.payment.vnpay.expiry-grace-minutes (mặc định 2), bù lệch đồng hồ với VNPay.
 *       Tới mốc này IPN thành công không còn được cộng Credit và đơn PENDING mới bị chuyển EXPIRED khi xem đơn.
 * </ul>
 */
@Component
public class PaymentDeadline {

    private final ConfigService configService;
    private final Clock clock;
    private final long graceMinutes;

    public PaymentDeadline(
            ConfigService configService,
            Clock clock,
            @Value("${app.payment.vnpay.expiry-grace-minutes:2}") long graceMinutes) {
        this.configService = configService;
        this.clock = clock;
        this.graceMinutes = graceMinutes;
    }

    /** Giờ hiện tại của server; trả kèm response (serverTime) để FE bù lệch đồng hồ. */
    public Instant now() {
        return clock.instant();
    }

    /** Hạn thanh toán người dùng thấy và VNPay dùng (vnp_ExpireDate). */
    public Instant expiresAt(Instant createdAt) {
        int expireMinutes = configService.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES);
        return createdAt.plus(expireMinutes, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
    }

    /** Mốc đóng hẳn đơn phía server: hạn thanh toán cộng dung sai lệch giờ với VNPay. */
    public Instant closesAt(Instant createdAt) {
        return expiresAt(createdAt).plus(graceMinutes, ChronoUnit.MINUTES);
    }

    /** Đơn tạo SAU mốc này còn trong hạn thanh toán (now - payment_order_expire_minutes). */
    public Instant activeCreatedAfter() {
        int expireMinutes = configService.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES);
        return now().minus(expireMinutes, ChronoUnit.MINUTES);
    }

    /** Đơn PENDING tạo TRƯỚC mốc này đã qua hạn cộng dung sai, được chuyển EXPIRED. */
    public Instant closedCreatedBefore() {
        return activeCreatedAfter().minus(graceMinutes, ChronoUnit.MINUTES);
    }

    /** Đơn tạo lúc createdAt đã qua hạn thanh toán (chưa tính dung sai) chưa. */
    public boolean isPastExpiresAt(Instant createdAt) {
        return !now().isBefore(expiresAt(createdAt));
    }

    /** Đơn tạo lúc createdAt đã qua mốc đóng hẳn chưa. */
    public boolean isClosed(Instant createdAt) {
        return now().isAfter(closesAt(createdAt));
    }
}
