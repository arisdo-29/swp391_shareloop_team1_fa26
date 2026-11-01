package com.shareloop.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.report.entity.ReportReason;
import com.shareloop.setting.ConfigKey;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** ConfigKey (module setting) không phụ thuộc module report, nên giữ danh sách mã riêng; test này giữ hai bên khớp nhau. */
class ReportReasonConfigSyncTest {

    @Test
    void seriousReasonCodesMatchReportReasonEnum() {
        assertThat(ConfigKey.REPORT_SERIOUS_REASONS.allowedCodes())
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(ReportReason.values()).map(Enum::name).toList());
    }

    @Test
    void defaultSeriousReasonsAreValidCodes() {
        assertThat(Arrays.asList(ConfigKey.REPORT_SERIOUS_REASONS.defaultValue().split(",")))
                .allSatisfy(code -> assertThat(ConfigKey.REPORT_SERIOUS_REASONS.allowedCodes())
                        .contains(code));
    }
}
