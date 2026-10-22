package com.shareloop.wallet.mapper;

import com.shareloop.wallet.dto.CreditTypeTotal;
import com.shareloop.wallet.dto.FinanceSummaryResponse;
import com.shareloop.wallet.dto.TopUpReconciliation;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Dựng file CSV từ báo cáo tài chính (FR-111). UTF-8 có BOM để Excel hiển thị đúng tiếng Việt, xuống dòng CRLF
 * theo RFC 4180.
 *
 * <p>File chia thành từng phần: dòng tiêu đề tiếng Việt, dòng header trùng tên trường của
 * {@link FinanceSummaryResponse}, rồi các dòng dữ liệu; hai phần cách nhau một dòng trống. Mọi ô đều là số, mã
 * enum hoặc nhãn cố định không chứa dấu phẩy nên không cần bọc ngoặc kép.
 */
@Component
public class FinanceReportCsvWriter {

    private static final String BOM = "﻿";
    private static final String CRLF = "\r\n";
    private static final String UNBOUNDED = "không giới hạn";

    public byte[] write(FinanceSummaryResponse report, LocalDate from, LocalDate to) {
        StringBuilder csv = new StringBuilder(BOM);

        line(csv, "Báo cáo tài chính ShareLoop");
        line(csv, "Từ ngày", from != null ? from : UNBOUNDED);
        line(csv, "Đến ngày", to != null ? to : UNBOUNDED);
        csv.append(CRLF);

        line(csv, "Tiền mặt thu");
        line(csv, "cashOrderCount", "cashAmountVnd");
        line(csv, report.cashOrderCount(), report.cashAmountVnd());
        csv.append(CRLF);

        creditSection(csv, "Credit cộng vào", report.creditInByType(), report.creditInTotal());
        csv.append(CRLF);
        creditSection(csv, "Credit trừ ra", report.creditOutByType(), report.creditOutTotal());
        csv.append(CRLF);

        TopUpReconciliation reconciliation = report.reconciliation();
        line(csv, "Đối chiếu TopUp");
        line(csv, "cashCredits", "topUpCredits", "difference", "matched");
        line(
                csv,
                reconciliation.cashCredits(),
                reconciliation.topUpCredits(),
                reconciliation.difference(),
                reconciliation.matched());

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Tên file tải về, ví dụ finance-report_2026-10-01_2026-10-17.csv; thiếu mốc nào thì ghi start / now. */
    public String fileName(LocalDate from, LocalDate to) {
        return "finance-report_" + (from != null ? from : "start") + "_" + (to != null ? to : "now") + ".csv";
    }

    // Dòng "Tổng" lấy amount từ creditInTotal / creditOutTotal của báo cáo, entryCount cộng từ các dòng theo type.
    private static void creditSection(StringBuilder csv, String title, List<CreditTypeTotal> byType, long total) {
        line(csv, title);
        line(csv, "type", "amount", "entryCount");
        for (CreditTypeTotal row : byType) {
            line(csv, row.type(), row.amount(), row.entryCount());
        }
        long entryCount = byType.stream().mapToLong(CreditTypeTotal::entryCount).sum();
        line(csv, "Tổng", total, entryCount);
    }

    private static void line(StringBuilder csv, Object... cells) {
        csv.append(Arrays.stream(cells).map(String::valueOf).collect(Collectors.joining(",")))
                .append(CRLF);
    }
}
