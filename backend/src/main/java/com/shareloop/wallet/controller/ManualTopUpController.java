package com.shareloop.wallet.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.wallet.dto.TopUpOrderDetailResponse;
import com.shareloop.wallet.service.ManualTopUpService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet/topup-orders")
public class ManualTopUpController {
    private final ManualTopUpService service;

    public ManualTopUpController(ManualTopUpService service) {
        this.service = service;
    }

    @GetMapping("/{orderCode}")
    public TopUpOrderDetailResponse get(@CurrentUserId Long userId, @PathVariable String orderCode) {
        return service.getOrder(userId, orderCode);
    }

    @PostMapping("/{orderCode}/mark-paid")
    public TopUpOrderDetailResponse markPaid(@CurrentUserId Long userId, @PathVariable String orderCode) {
        return service.markPaid(userId, orderCode);
    }
}
