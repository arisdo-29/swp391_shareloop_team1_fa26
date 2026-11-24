package com.shareloop.wallet.mapper;

import com.shareloop.wallet.dto.AdminTopUpOrderResponse;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Dựng file CSV danh sách đơn nạp cho Admin. UTF-8 có BOM để Excel đọc đúng tiếng Việt, xuống dòng CRLF. Ô chứa dấu
 * phẩy, ngoặc kép hoặc xuống dòng được bọc ngoặc kép (tên người và ghi chú có thể chứa các ký tự này).
 */
@Component
public class TopUpOrderCsvWriter {

    private static final String BOM = "﻿";
    private static final String CRLF = "\r\n";
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] write(List<AdminTopUpOrderResponse> orders) {
        StringBuilder csv = new StringBuilder(BOM);
        line(
                csv,
                "Mã đơn",
                "Người nạp",
                "Email",
                "Số tiền (VND)",
                "Số Credit",
                "Cổng thanh toán",
                "Trạng thái",
                "Cần đối soát",
                "Mã giao dịch VNPay",
                "Tạo lúc",
                "Thanh toán lúc",
                "Ghi chú");
        for (AdminTopUpOrderResponse order : orders) {
            line(
                    csv,
                    order.orderCode(),
                    order.userName(),
                    order.userEmail(),
                    String.valueOf(order.amountVnd()),
                    String.valueOf(order.credits()),
                    order.provider().name(),
                    statusLabel(order.status()),
                    order.needsReconcile() ? "Có" : "Không",
                    order.providerTxnId(),
                    format(order.createdAt()),
                    format(order.paidAt()),
                    order.reviewNote());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String statusLabel(PaymentOrderStatus status) {
        return switch (status) {
            case PENDING -> "Đang chờ";
            case SUCCESS -> "Thành công";
            case FAILED -> "Thất bại";
            case EXPIRED -> "Hết hạn";
            case CANCELLED -> "Đã hủy";
        };
    }

    private static String format(Instant instant) {
        return instant == null ? "" : DISPLAY_DATE_TIME.format(instant.atZone(VIETNAM_ZONE));
    }

    private static void line(StringBuilder csv, String... cells) {
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(escape(cells[index]));
        }
        csv.append(CRLF);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote =
                value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        return needsQuote ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
