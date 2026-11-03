package com.shareloop.wallet.mapper;

import com.shareloop.wallet.dto.CreditTypeTotal;
import com.shareloop.wallet.dto.FinanceSummaryResponse;
import com.shareloop.wallet.dto.TopUpReconciliation;
import com.shareloop.wallet.entity.CreditTxType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Dựng file CSV từ báo cáo tài chính (FR-111). UTF-8 có BOM để Excel hiển thị đúng tiếng Việt, xuống dòng CRLF
 * theo RFC 4180.
 *
 * <p>File chia thành từng phần: dòng tiêu đề tiếng Việt, dòng header tiếng Việt, rồi các dòng dữ liệu; hai phần
 * cách nhau một dòng trống. Mọi ô đều là số hoặc nhãn cố định không chứa dấu phẩy nên không cần bọc ngoặc kép. Số
 * giữ ở dạng số thuần (không có dấu phân cách hàng nghìn) để Excel còn tính toán được.
 */
@Component
public class FinanceReportCsvWriter {

    private static final String BOM = "﻿";
    private static final String CRLF = "\r\n";
    private static final String UNBOUNDED = "không giới hạn";
    private static final String MATCHED = "Khớp";
    private static final String MISMATCHED = "Lệch";
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] write(FinanceSummaryResponse report, LocalDate from, LocalDate to) {
        StringBuilder csv = new StringBuilder(BOM);

        line(csv, "Báo cáo tài chính ShareLoop");
        line(csv, "Từ ngày", formatDate(from));
        line(csv, "Đến ngày", formatDate(to));
        csv.append(CRLF);

        line(csv, "Tiền mặt thu");
        line(csv, "Số đơn nạp thành công", "Số tiền thu (VND)");
        line(csv, report.cashOrderCount(), report.cashAmountVnd());
        csv.append(CRLF);

        creditSection(csv, "Credit cộng vào", report.creditInByType(), report.creditInTotal());
        csv.append(CRLF);
        creditSection(csv, "Credit trừ ra", report.creditOutByType(), report.creditOutTotal());
        csv.append(CRLF);

        TopUpReconciliation reconciliation = report.reconciliation();
        line(csv, "Đối chiếu nạp Credit");
        line(csv, "Credit quy đổi từ tiền mặt", "Credit nạp ghi sổ", "Chênh lệch", "Kết quả");
        line(
                csv,
                reconciliation.cashCredits(),
                reconciliation.topUpCredits(),
                reconciliation.difference(),
                reconciliation.matched() ? MATCHED : MISMATCHED);

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Tên file tải về, ví dụ finance-report_2026-10-01_2026-10-17.csv; thiếu mốc nào thì ghi start / now. */
    public String fileName(LocalDate from, LocalDate to) {
        return "finance-report_" + (from != null ? from : "start") + "_" + (to != null ? to : "now") + ".csv";
    }

    // Dòng "Tổng" lấy amount từ creditInTotal / creditOutTotal của báo cáo, entryCount cộng từ các dòng theo type.
    private static void creditSection(StringBuilder csv, String title, List<CreditTypeTotal> byType, long total) {
        line(csv, title);
        line(csv, "Loại giao dịch", "Số Credit", "Số lượt");
        for (CreditTypeTotal row : byType) {
            line(csv, typeLabel(row.type()), row.amount(), row.entryCount());
        }
        long entryCount = byType.stream().mapToLong(CreditTypeTotal::entryCount).sum();
        line(csv, "Tổng", total, entryCount);
    }

    // Người đọc báo cáo là Admin nên ghi nhãn tiếng Việt thay cho mã enum.
    private static String typeLabel(CreditTxType type) {
        return switch (type) {
            case TOP_UP -> "Nạp Credit";
            case POST_FEE -> "Phí đăng bài";
            case EDIT_FEE -> "Phí sửa bài";
            case RENEW_FEE -> "Phí gia hạn";
            case BOOST_FEE -> "Phí đẩy bài";
            case AI_SEARCH_FEE -> "Phí tìm bằng AI";
            case ADMIN_ADJUST -> "Admin điều chỉnh";
        };
    }

    // Ngày hiển thị dd/MM/yyyy theo thói quen người Việt; không truyền mốc nào thì ghi "không giới hạn".
    private static String formatDate(LocalDate date) {
        return date != null ? date.format(DISPLAY_DATE) : UNBOUNDED;
    }

    private static void line(StringBuilder csv, Object... cells) {
        csv.append(Arrays.stream(cells).map(String::valueOf).collect(Collectors.joining(",")))
                .append(CRLF);
    }
}
