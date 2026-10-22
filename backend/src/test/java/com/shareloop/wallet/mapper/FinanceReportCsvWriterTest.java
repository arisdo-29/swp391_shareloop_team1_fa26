package com.shareloop.wallet.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.wallet.dto.FinanceSummaryResponse;
import com.shareloop.wallet.dto.TopUpReconciliation;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FinanceReportCsvWriterTest {

    private final FinanceReportCsvWriter writer = new FinanceReportCsvWriter();

    @Test
    @DisplayName("Không truyền from/to và chưa có giao dịch: ghi 'không giới hạn', dòng Tổng bằng 0")
    void write_noDatesAndNoEntries_writesUnboundedAndZeroTotals() {
        FinanceSummaryResponse report = new FinanceSummaryResponse(
                null, null, 0L, 0L, 0L, List.of(), 0L, List.of(), new TopUpReconciliation(50L, 40L, 10L, false));

        String csv = new String(writer.write(report, null, null), StandardCharsets.UTF_8);

        assertThat(csv.substring(1).split("\r\n", -1))
                .containsExactly(
                        "Báo cáo tài chính ShareLoop",
                        "Từ ngày,không giới hạn",
                        "Đến ngày,không giới hạn",
                        "",
                        "Tiền mặt thu",
                        "cashOrderCount,cashAmountVnd",
                        "0,0",
                        "",
                        "Credit cộng vào",
                        "type,amount,entryCount",
                        "Tổng,0,0",
                        "",
                        "Credit trừ ra",
                        "type,amount,entryCount",
                        "Tổng,0,0",
                        "",
                        "Đối chiếu TopUp",
                        "cashCredits,topUpCredits,difference,matched",
                        "50,40,10,false",
                        "");
    }

    @Test
    @DisplayName("Tên file theo khoảng ngày, thiếu mốc nào thì ghi start / now")
    void fileName_followsDateRange() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 17);

        assertThat(writer.fileName(from, to)).isEqualTo("finance-report_2026-10-01_2026-10-17.csv");
        assertThat(writer.fileName(from, null)).isEqualTo("finance-report_2026-10-01_now.csv");
        assertThat(writer.fileName(null, to)).isEqualTo("finance-report_start_2026-10-17.csv");
        assertThat(writer.fileName(null, null)).isEqualTo("finance-report_start_now.csv");
    }
}
