package com.shareloop.report.event;

import static org.mockito.Mockito.verify;

import com.shareloop.report.service.ReportService;
import com.shareloop.request.event.NotAsDescribedReportedEvent;
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
    void onNotAsDescribed_opensReportForReceiverWithTheirReason() {
        // given
        NotAsDescribedReportedEvent event = new NotAsDescribedReportedEvent(100L, 10L, "Máy không bật được");

        // when
        listener.onNotAsDescribed(event);

        // then
        verify(reportService).openNotAsDescribedReport(10L, 100L, "Máy không bật được");
    }
}
