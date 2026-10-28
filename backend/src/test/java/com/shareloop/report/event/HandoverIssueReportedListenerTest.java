package com.shareloop.report.event;

import static org.mockito.Mockito.verify;

import com.shareloop.report.service.ReportService;
import com.shareloop.request.event.HandoverIssueReportedEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandoverIssueReportedListenerTest {
    @Mock
    ReportService reportService;

    @InjectMocks
    HandoverIssueReportedListener listener;

    @Test
    void onHandoverIssue_opensReportForReporterWithNoteAndEvidence() {
        // given
        HandoverIssueReportedEvent event = new HandoverIssueReportedEvent(100L, 10L, "Chưa nhận được đồ", List.of(7L));

        // when
        listener.onHandoverIssue(event);

        // then
        verify(reportService).openHandoverIssueReport(10L, 100L, "Chưa nhận được đồ", List.of(7L));
    }
}
