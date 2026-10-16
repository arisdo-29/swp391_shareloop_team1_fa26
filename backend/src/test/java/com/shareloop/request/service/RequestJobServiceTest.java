package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class RequestJobServiceTest {

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private ItemService itemService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ConfigService configService;

    @Mock
    private ReputationService reputationService;

    @Mock
    private ItemQueryService itemQueryService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private RequestJobService requestJobService;

    private final Instant now = Instant.parse("2026-10-16T12:00:00Z");

    @BeforeEach
    void setUp() {
        // Execute callback directly inside transactionTemplate.execute
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        requestJobService = new RequestJobService(
                requestRepository,
                itemService,
                notificationService,
                configService,
                reputationService,
                itemQueryService,
                null,
                transactionTemplate);
    }

    @Nested
    @DisplayName("expirePendingRequests (FR-119, BR-R06)")
    class ExpirePendingRequestsTests {

        @Test
        @DisplayName("Quá hạn 3 ngày PENDING thì tự huỷ sang CANCELLED và gửi notification")
        void expirePendingRequests_whenOverdue_cancelsAndNotifies() {
            when(configService.getInt(ConfigKey.REQUEST_PENDING_DAYS)).thenReturn(3);
            Instant cutoff = now.minus(3, ChronoUnit.DAYS);

            Request request = new Request(1L, 10L, null, "GIVE");
            ReflectionTestUtils.setField(request, "id", 101L);
            ReflectionTestUtils.setField(request, "createdAt", cutoff.minus(1, ChronoUnit.HOURS));

            when(requestRepository.findByStatusAndCreatedAtBefore(RequestStatus.PENDING, cutoff))
                    .thenReturn(List.of(request));
            when(requestRepository.findById(101L)).thenReturn(Optional.of(request));

            int cancelledCount = requestJobService.expirePendingRequests(now);

            assertThat(cancelledCount).isEqualTo(1);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
            assertThat(request.getCancelReason()).contains("Hết hạn");
            verify(requestRepository).save(request);
            verify(notificationService).notify(eq(10L), eq("REQUEST_EXPIRED"), anyString(), anyString(), contains("1"));
        }

        @Test
        @DisplayName("Request chưa quá hạn hoặc không còn PENDING thì không huỷ")
        void expirePendingRequests_whenNotOverdueOrNotPending_doesNotCancel() {
            when(configService.getInt(ConfigKey.REQUEST_PENDING_DAYS)).thenReturn(3);
            Instant cutoff = now.minus(3, ChronoUnit.DAYS);

            Request request = new Request(1L, 10L, null, "GIVE");
            ReflectionTestUtils.setField(request, "id", 101L);
            // Vừa tạo sau cutoff
            ReflectionTestUtils.setField(request, "createdAt", cutoff.plus(1, ChronoUnit.HOURS));

            when(requestRepository.findByStatusAndCreatedAtBefore(RequestStatus.PENDING, cutoff))
                    .thenReturn(List.of(request));
            when(requestRepository.findById(101L)).thenReturn(Optional.of(request));

            int cancelledCount = requestJobService.expirePendingRequests(now);

            assertThat(cancelledCount).isEqualTo(0);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING);
            verify(requestRepository, never()).save(request);
            verify(notificationService, never()).notify(anyLong(), anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("expireSilentReservedRequests (FR-120, BR-T10, FR-77, BR-C03)")
    class ExpireSilentReservedRequestsTests {

        @Test
        @DisplayName("Quá hạn 10 ngày RESERVED thì huỷ Request, nhả bài về APPROVED và bắn notification")
        void expireSilentReservedRequests_whenReservedOverdue_cancelsReleasesAndNotifies() {
            when(configService.getInt(ConfigKey.CHAT_OPEN_DAYS)).thenReturn(10);
            Instant cutoff = now.minus(10, ChronoUnit.DAYS);

            Request request = new Request(1L, 10L, null, "GIVE");
            ReflectionTestUtils.setField(request, "id", 201L);
            request.select(cutoff.minus(1, ChronoUnit.HOURS)); // status -> RESERVED, reservedAt set

            when(requestRepository.findSilentReservedRequests(cutoff)).thenReturn(List.of(request));
            when(requestRepository.findById(201L)).thenReturn(Optional.of(request));
            when(itemQueryService.getSummary(1L)).thenReturn(new ItemSummary(1L, 20L, null, null));

            int cancelledCount = requestJobService.expireSilentReservedRequests(now);

            assertThat(cancelledCount).isEqualTo(1);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
            assertThat(request.getCancelReason()).contains("10 ngày");
            verify(requestRepository).save(request);
            verify(itemService).release(1L);
            // Thông báo cho cả donor (20L) và receiver (10L)
            verify(notificationService).notify(eq(20L), eq("REQUEST_CANCELLED"), anyString(), anyString(), anyString());
            verify(notificationService).notify(eq(10L), eq("REQUEST_CANCELLED"), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Quá hạn 10 ngày ở AWAITING_LOGISTICS thì cũng huỷ và nhả bài")
        void expireSilentReservedRequests_whenAwaitingLogisticsOverdue_cancelsAndReleases() {
            when(configService.getInt(ConfigKey.CHAT_OPEN_DAYS)).thenReturn(10);
            Instant cutoff = now.minus(10, ChronoUnit.DAYS);

            Request request = new Request(1L, 10L, null, "GIVE");
            ReflectionTestUtils.setField(request, "id", 202L);
            request.select(cutoff.minus(2, ChronoUnit.HOURS));
            request.proposeSchedule("DIRECT", "FPT Gate", now.plus(1, ChronoUnit.DAYS), true, false, cutoff);

            when(requestRepository.findSilentReservedRequests(cutoff)).thenReturn(List.of(request));
            when(requestRepository.findById(202L)).thenReturn(Optional.of(request));
            when(itemQueryService.getSummary(1L)).thenReturn(new ItemSummary(1L, 20L, null, null));

            int cancelledCount = requestJobService.expireSilentReservedRequests(now);

            assertThat(cancelledCount).isEqualTo(1);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
            verify(itemService).release(1L);
        }
    }

    @Nested
    @DisplayName("autoConfirmHandoverRequests (FR-87, BR-T06)")
    class AutoConfirmHandoverRequestsTests {

        @Test
        @DisplayName(
                "Donor đã xác nhận, receiver im lặng quá 3 ngày -> tự động COMPLETED, markTraded, reputation và notification")
        void autoConfirmHandoverRequests_whenDonorConfirmedAndReceiverSilent_autoCompletes() {
            when(configService.getInt(ConfigKey.AUTO_CONFIRM_DAYS)).thenReturn(3);
            Instant cutoff = now.minus(3, ChronoUnit.DAYS);

            Request request = new Request(1L, 10L, null, "GIVE");
            ReflectionTestUtils.setField(request, "id", 301L);
            request.select(now.minus(5, ChronoUnit.DAYS));
            request.proposeSchedule(
                    "DIRECT", "Gate", now.plus(1, ChronoUnit.DAYS), true, false, now.minus(4, ChronoUnit.DAYS));
            request.confirmLogistics(false, false, now.minus(4, ChronoUnit.DAYS)); // status -> LOGISTICS_CONFIRMED
            request.confirmHandover(
                    true, null, cutoff.minus(2, ChronoUnit.HOURS)); // donor confirms -> AWAITING_HANDOVER

            when(requestRepository.findOverdueHandoverRequests(cutoff)).thenReturn(List.of(request));
            when(requestRepository.findById(301L)).thenReturn(Optional.of(request));
            when(itemQueryService.getSummary(1L)).thenReturn(new ItemSummary(1L, 20L, null, null));

            int completedCount = requestJobService.autoConfirmHandoverRequests(now);

            assertThat(completedCount).isEqualTo(1);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
            assertThat(request.isAutoConfirmed()).isTrue();
            assertThat(request.getAsDescribed()).isTrue();
            assertThat(request.getCompletedAt()).isEqualTo(now);

            verify(requestRepository).save(request);
            verify(itemService).markTraded(1L);
            verify(reputationService).onCompleted(301L);
            verify(notificationService)
                    .notify(eq(20L), eq("TRANSACTION_COMPLETED"), anyString(), anyString(), anyString());
            verify(notificationService)
                    .notify(eq(10L), eq("TRANSACTION_COMPLETED"), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName(
                "Receiver đã xác nhận, donor im lặng quá 3 ngày trong giao dịch SWAP -> tự động COMPLETED cả 2 bài")
        void autoConfirmHandoverRequests_whenReceiverConfirmedInSwap_autoCompletesBothItems() {
            when(configService.getInt(ConfigKey.AUTO_CONFIRM_DAYS)).thenReturn(3);
            Instant cutoff = now.minus(3, ChronoUnit.DAYS);

            Request request = new Request(1L, 10L, 2L, "SWAP");
            ReflectionTestUtils.setField(request, "id", 302L);
            request.select(now.minus(5, ChronoUnit.DAYS));
            request.proposeSchedule(
                    "DIRECT", "Gate", now.plus(1, ChronoUnit.DAYS), true, false, now.minus(4, ChronoUnit.DAYS));
            request.confirmLogistics(false, false, now.minus(4, ChronoUnit.DAYS));
            request.confirmHandover(
                    false, true, cutoff.minus(1, ChronoUnit.HOURS)); // receiver confirms -> AWAITING_HANDOVER

            when(requestRepository.findOverdueHandoverRequests(cutoff)).thenReturn(List.of(request));
            when(requestRepository.findById(302L)).thenReturn(Optional.of(request));
            when(itemQueryService.getSummary(1L)).thenReturn(new ItemSummary(1L, 20L, null, null));

            int completedCount = requestJobService.autoConfirmHandoverRequests(now);

            assertThat(completedCount).isEqualTo(1);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
            assertThat(request.isAutoConfirmed()).isTrue();

            verify(itemService).markTraded(1L);
            verify(itemService).markTraded(2L); // Món đổi offeredItemId
            verify(reputationService).onCompleted(302L);
        }

        @Test
        @DisplayName("Chưa đủ 3 ngày kể từ lúc một bên xác nhận -> không auto confirm")
        void autoConfirmHandoverRequests_whenNotOverdue_doesNotComplete() {
            when(configService.getInt(ConfigKey.AUTO_CONFIRM_DAYS)).thenReturn(3);
            Instant cutoff = now.minus(3, ChronoUnit.DAYS);

            Request request = new Request(1L, 10L, null, "GIVE");
            ReflectionTestUtils.setField(request, "id", 303L);
            request.select(now.minus(4, ChronoUnit.DAYS));
            request.proposeSchedule(
                    "DIRECT", "Gate", now.plus(1, ChronoUnit.DAYS), true, false, now.minus(3, ChronoUnit.DAYS));
            request.confirmLogistics(false, false, now.minus(3, ChronoUnit.DAYS));
            // Confirm mới 1 ngày trước (sau cutoff)
            request.confirmHandover(true, null, cutoff.plus(1, ChronoUnit.DAYS));

            when(requestRepository.findOverdueHandoverRequests(cutoff)).thenReturn(List.of(request));
            when(requestRepository.findById(303L)).thenReturn(Optional.of(request));

            int completedCount = requestJobService.autoConfirmHandoverRequests(now);

            assertThat(completedCount).isEqualTo(0);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_HANDOVER);
            verify(itemService, never()).markTraded(anyLong());
        }
    }

    @Nested
    @DisplayName("Transaction Isolation & Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Một request bị lỗi RuntimeException thì bắt lỗi và bỏ qua, không chặn request tiếp theo")
        void expirePendingRequests_whenOneFails_processesOthers() {
            when(configService.getInt(ConfigKey.REQUEST_PENDING_DAYS)).thenReturn(3);
            Instant cutoff = now.minus(3, ChronoUnit.DAYS);

            Request r1 = new Request(1L, 10L, null, "GIVE");
            ReflectionTestUtils.setField(r1, "id", 401L);
            ReflectionTestUtils.setField(r1, "createdAt", cutoff.minus(1, ChronoUnit.HOURS));

            Request r2 = new Request(2L, 20L, null, "GIVE");
            ReflectionTestUtils.setField(r2, "id", 402L);
            ReflectionTestUtils.setField(r2, "createdAt", cutoff.minus(1, ChronoUnit.HOURS));

            when(requestRepository.findByStatusAndCreatedAtBefore(RequestStatus.PENDING, cutoff))
                    .thenReturn(List.of(r1, r2));

            when(requestRepository.findById(401L)).thenThrow(new RuntimeException("Database timeout"));
            when(requestRepository.findById(402L)).thenReturn(Optional.of(r2));

            int count = requestJobService.expirePendingRequests(now);

            assertThat(count).isEqualTo(1);
            assertThat(r2.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        }
    }
}
