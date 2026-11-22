package com.shareloop.wallet.mapper;

import com.shareloop.wallet.dto.AdminLedgerRow;
import com.shareloop.wallet.entity.CreditTxType;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/** CSV lịch sử giao dịch Credit toàn hệ thống. UTF-8 có BOM, xuống dòng CRLF, ô có dấu phẩy được bọc ngoặc kép. */
@Component
public class LedgerCsvWriter {

    private static final String BOM = "﻿";
    private static final String CRLF = "\r\n";
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] write(List<AdminLedgerRow> rows) {
        StringBuilder csv = new StringBuilder(BOM);
        line(
                csv,
                "Thời gian",
                "Người dùng",
                "Email",
                "Loại",
                "Số Credit",
                "Số dư sau",
                "Bài liên quan",
                "Mã đơn nạp",
                "Ghi chú",
                "Người thực hiện");
        for (AdminLedgerRow row : rows) {
            line(
                    csv,
                    row.createdAt() == null
                            ? ""
                            : DISPLAY_DATE_TIME.format(row.createdAt().atZone(VIETNAM_ZONE)),
                    row.userName(),
                    row.userEmail(),
                    typeLabel(row.type()),
                    String.valueOf(row.amount()),
                    String.valueOf(row.balanceAfter()),
                    row.itemTitle(),
                    row.orderCode(),
                    row.note(),
                    row.createdByName());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String typeLabel(CreditTxType type) {
        return switch (type) {
            case TOP_UP -> "Nạp Credit";
            case POST_FEE -> "Phí đăng bài";
            case EDIT_FEE -> "Phí sửa bài";
            case RENEW_FEE -> "Phí gia hạn";
            case BOOST_FEE -> "Phí đẩy bài";
            case AI_SEARCH_FEE -> "Phí trợ lý AI";
            case ADMIN_ADJUST -> "Admin điều chỉnh";
        };
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
