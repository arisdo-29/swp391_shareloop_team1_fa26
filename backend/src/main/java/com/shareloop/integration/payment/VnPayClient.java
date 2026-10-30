package com.shareloop.integration.payment;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.wallet.entity.PaymentProvider;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
// @Lazy(false): prod bật lazy-initialization, nhưng thiếu biến VNPAY phải làm app lỗi ngay lúc khởi động.
@Lazy(false)
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "vnpay")
public class VnPayClient implements PaymentGatewayClient {
    private final ConfigService configService;
    private final Clock clock;
    private final String payUrl;
    private final String returnUrl;
    private final String tmnCode;
    private final String secret;

    public VnPayClient(
            ConfigService configService,
            Clock clock,
            @Value("${app.payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
            @Value("${app.payment.vnpay.return-url:}") String returnUrl,
            @Value("${app.payment.vnpay.tmn-code:}") String tmnCode,
            @Value("${app.payment.vnpay.hash-secret:}") String secret,
            @Value("${app.payment.vnpay.fe-result-url:}") String feResultUrl) {
        requireConfigured(payUrl, returnUrl, tmnCode, secret, feResultUrl);
        this.configService = configService;
        this.clock = clock;
        this.payUrl = payUrl;
        this.returnUrl = returnUrl;
        this.tmnCode = tmnCode;
        this.secret = secret;
    }

    // Báo đúng tên biến môi trường còn thiếu để người deploy sửa ngay, thay vì để lỗi chữ ký lúc thanh toán.
    private static void requireConfigured(
            String payUrl, String returnUrl, String tmnCode, String secret, String feResultUrl) {
        List<String> missing = new ArrayList<>();
        if (payUrl.isBlank()) missing.add("VNPAY_PAY_URL");
        if (returnUrl.isBlank()) missing.add("VNPAY_RETURN_URL");
        if (tmnCode.isBlank()) missing.add("VNPAY_TMN_CODE");
        if (secret.isBlank()) missing.add("VNPAY_HASH_SECRET");
        if (feResultUrl.isBlank()) missing.add("VNPAY_FE_RESULT_URL");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("PAYMENT_PROVIDER=vnpay but these environment variables are missing: "
                    + String.join(", ", missing));
        }
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.VNPAY;
    }

    @Override
    public String buildRedirectUrl(String orderCode, long amountVnd) {
        var now = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
        var formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", Long.toString(Math.multiplyExact(amountVnd, 100)));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", orderCode);
        params.put("vnp_OrderInfo", "Nap Credit ShareLoop " + orderCode);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        var attributes = RequestContextHolder.getRequestAttributes();
        params.put(
                "vnp_IpAddr",
                attributes instanceof ServletRequestAttributes request
                        ? request.getRequest().getRemoteAddr()
                        : "127.0.0.1");
        params.put("vnp_CreateDate", now.format(formatter));
        params.put(
                "vnp_ExpireDate",
                now.plusMinutes(configService.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES))
                        .format(formatter));
        return payUrl + "?" + VnPaySigner.encodeParameters(params) + "&vnp_SecureHash="
                + VnPaySigner.sign(params, secret);
    }
}
