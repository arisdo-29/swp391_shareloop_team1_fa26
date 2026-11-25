package com.shareloop.report.event;

import static org.mockito.Mockito.verify;

import com.shareloop.item.event.ItemApprovedEvent;
import com.shareloop.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemApprovedListenerTest {
    @Mock
    ReportService reportService;

    @InjectMocks
    ItemApprovedListener listener;

    @Test
    void onItemApproved_asksReportModuleToRelistItem() {
        // given
        ItemApprovedEvent event = new ItemApprovedEvent(200L);

        // when
        listener.onItemApproved(event);

        // then
        verify(reportService).relistItemAfterReapproval(200L);
    }
}
