package com.shareloop.wallet.controller;

import com.shareloop.wallet.dto.VnPayIpnResponse;
import com.shareloop.wallet.service.VnPayCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "vn-pay-callback-controller", description = "Tiếp nhận kết quả thanh toán từ VNPAY")
@RestController
@RequestMapping("/api/v1/payments/vnpay")
public class VnPayCallbackController {
    private static final Logger log = LoggerFactory.getLogger(VnPayCallbackController.class);
    private final VnPayCallbackService service;
    private final String feResultUrl;

    public VnPayCallbackController(
            VnPayCallbackService service,
            @Value("${app.payment.vnpay.fe-result-url:http://localhost:5173/credit}") String feResultUrl) {
        this.service = service;
        this.feResultUrl = feResultUrl;
    }

    @Operation(
            summary = "Nhận thông báo kết quả thanh toán VNPAY",
            description =
                    "VNPAY gọi trực tiếp, không cần Bearer Token. Kiểm chữ ký và số tiền, cập nhật đơn nạp và cộng Credit khi thanh toán thành công; callback lặp không cộng thêm.")
    @GetMapping("/ipn")
    public VnPayIpnResponse ipn(@RequestParam Map<String, String> params) {
        try {
            return service.processIpn(params);
        } catch (RuntimeException ex) {
            log.error("VNPAY IPN processing failed", ex);
            return new VnPayIpnResponse("99", "Unknown error");
        }
    }

    @Operation(
            summary = "Chuyển người dùng về trang kết quả thanh toán",
            description =
                    "Không cần Bearer Token. Kiểm chữ ký rồi chuyển hướng 302 về giao diện với mã đơn và trạng thái. Endpoint này không cộng Credit; kết quả cộng Credit được xử lý qua IPN.")
    @GetMapping("/return")
    public ResponseEntity<Void> paymentReturn(@RequestParam Map<String, String> params) {
        boolean verified = service.verify(params);
        String status = !verified ? "INVALID_SIGNATURE" : service.isSuccessful(params) ? "SUCCESS" : "FAILED";
        URI location = UriComponentsBuilder.fromUriString(feResultUrl)
                .replaceQueryParam("orderCode", "{orderCode}")
                .replaceQueryParam("status", status)
                .encode()
                .buildAndExpand(verified ? params.getOrDefault("vnp_TxnRef", "") : "")
                .toUri();
        return ResponseEntity.status(302).location(location).build();
    }
}
