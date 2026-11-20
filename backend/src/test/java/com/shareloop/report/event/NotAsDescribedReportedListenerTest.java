package com.shareloop.report.event;

import static org.mockito.Mockito.verify;

import com.shareloop.report.service.ReportService;
import com.shareloop.request.event.NotAsDescribedReportedEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotAsDescribedReportedListenerTest {
    @Mock
    ReportService reportService;

    @InjectMocks
    NotAsDescribedReportedListener listener;

    @Test
    void onNotAsDescribed_opensReportForReporterWithReasonAndEvidence() {
        // given
        NotAsDescribedReportedEvent event =
                new NotAsDescribedReportedEvent(100L, 10L, 200L, 20L, "Máy không bật được", List.of(5L));

        // when
        listener.onNotAsDescribed(event);

        // then
        verify(reportService).openRefusalReport(10L, 100L, 200L, 20L, "Máy không bật được", List.of(5L));
    }
}
