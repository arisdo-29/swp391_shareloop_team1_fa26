package com.shareloop.request.job;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.request.service.RequestJobService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestJobTest {

    @Mock
    private RequestJobService requestJobService;

    private Clock fixedClock;
    private RequestJob requestJob;

    @BeforeEach
    void setUp() {
        Instant fixedInstant = Instant.parse("2026-10-16T12:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        requestJob = new RequestJob(requestJobService, fixedClock);
    }

    @Test
    @DisplayName("run() thực thi đầy đủ 3 tác vụ nền theo mốc thời gian của Clock")
    void run_executesAllBackgroundTasks() {
        Instant expectedNow = fixedClock.instant();
        when(requestJobService.expirePendingRequests(expectedNow)).thenReturn(2);
        when(requestJobService.expireSilentReservedRequests(expectedNow)).thenReturn(1);
        when(requestJobService.autoConfirmHandoverRequests(expectedNow)).thenReturn(3);

        requestJob.run();

        verify(requestJobService).expirePendingRequests(expectedNow);
        verify(requestJobService).expireSilentReservedRequests(expectedNow);
        verify(requestJobService).autoConfirmHandoverRequests(expectedNow);
    }
}
