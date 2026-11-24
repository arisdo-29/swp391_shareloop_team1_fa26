package com.shareloop.wallet.controller;

import com.shareloop.common.api.PageResponse;
import com.shareloop.common.security.CurrentUserId;
import com.shareloop.wallet.dto.AdminTopUpOrderDetail;
import com.shareloop.wallet.dto.AdminTopUpOrderResponse;
import com.shareloop.wallet.dto.AdminTopUpSummary;
import com.shareloop.wallet.dto.ReconcileTopUpRequest;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.service.AdminTopUpService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trang Giao dịch nạp tiền của Admin. Chỉ Admin truy cập được (/api/v1/admin/** do SecurityConfig bảo vệ). */
@RestController
@RequestMapping("/api/v1/admin/finance/topup-orders")
@Validated
public class AdminTopUpController {
    private static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AdminTopUpService service;

    public AdminTopUpController(AdminTopUpService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdminTopUpOrderResponse> list(
            // Bỏ trống provider nghĩa là lấy mọi cổng, bỏ trống status nghĩa là lấy mọi trạng thái.
            @RequestParam(required = false) PaymentProvider provider,
            @RequestParam(required = false) PaymentOrderStatus status,
            // q khớp một phần mã đơn, họ tên hoặc email người nạp.
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean paidDate,
            @RequestParam(defaultValue = "false") boolean needsReconcile,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(provider, status, q, from, to, paidDate, needsReconcile, page, size);
    }

    @GetMapping("/summary")
    public AdminTopUpSummary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean paidDate) {
        return service.summarize(from, to, paidDate);
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) PaymentProvider provider,
            @RequestParam(required = false) PaymentOrderStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean paidDate,
            @RequestParam(defaultValue = "false") boolean needsReconcile) {
        byte[] body = service.exportCsv(provider, status, q, from, to, paidDate, needsReconcile);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("giao-dich-nap-tien.csv", StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(TEXT_CSV_UTF8)
                .body(body);
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) PaymentProvider provider,
            @RequestParam(required = false) PaymentOrderStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean paidDate,
            @RequestParam(defaultValue = "false") boolean needsReconcile) {
        byte[] body = service.exportExcel(provider, status, q, from, to, paidDate, needsReconcile);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("giao-dich-nap-tien.xlsx", StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(XLSX)
                .body(body);
    }

    @GetMapping("/{orderCode}")
    public AdminTopUpOrderDetail detail(@PathVariable String orderCode) {
        return service.getDetail(orderCode);
    }

    @PostMapping("/{orderCode}/reconcile")
    public AdminTopUpOrderDetail reconcile(
            @PathVariable String orderCode,
            @CurrentUserId Long adminId,
            @Valid @RequestBody ReconcileTopUpRequest request) {
        return service.reconcile(adminId, orderCode, request);
    }
}
