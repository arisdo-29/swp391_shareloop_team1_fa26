package com.shareloop.wallet.controller;

import com.shareloop.wallet.dto.AdminLedgerPage;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.service.AdminLedgerService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lịch sử giao dịch Credit toàn hệ thống cho Admin. Chỉ Admin truy cập được (/api/v1/admin/** do SecurityConfig bảo vệ). */
@RestController
@RequestMapping("/api/v1/admin/finance")
@Validated
public class AdminLedgerController {
    private static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final AdminLedgerService service;

    public AdminLedgerController(AdminLedgerService service) {
        this.service = service;
    }

    @GetMapping("/ledger")
    public AdminLedgerPage list(
            @RequestParam(required = false) CreditTxType type,
            @RequestParam(required = false) Long userId,
            // q khớp một phần tên hoặc email người dùng, ghi chú hoặc mã đơn nạp.
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(type, userId, q, from, to, page, size);
    }

    @GetMapping("/ledger.csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) CreditTxType type,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = service.exportCsv(type, userId, q, from, to);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("lich-su-giao-dich-credit.csv", StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(TEXT_CSV_UTF8)
                .body(body);
    }
}
