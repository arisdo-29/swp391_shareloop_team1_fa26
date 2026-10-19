package com.shareloop.wallet.controller;

import com.shareloop.wallet.dto.VnPayIpnResponse;
import com.shareloop.wallet.service.VnPayCallbackService;
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

    @GetMapping("/ipn")
    public VnPayIpnResponse ipn(@RequestParam Map<String, String> params) {
        try {
            return service.processIpn(params);
        } catch (RuntimeException ex) {
            log.error("VNPAY IPN processing failed", ex);
            return new VnPayIpnResponse("99", "Unknown error");
        }
    }

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
