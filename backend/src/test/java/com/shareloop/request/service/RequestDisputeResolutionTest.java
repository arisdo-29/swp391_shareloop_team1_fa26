package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.ItemDisputeOutcome;
import com.shareloop.request.dto.ItemDisputeOutcome.Choice;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.ContactInfo;
import com.shareloop.user.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Kết luận khiếu nại sự cố giao nhận: xử lý từng món khi chấp nhận (GN-14), hoàn tất khi bác bỏ. */
@ExtendWith(MockitoExtension.class)
class RequestDisputeResolutionTest {

    private static final long REQUEST_ID = 101L;
    private static final long ITEM_ID = 1L;
    private static final long OFFERED_ITEM_ID = 50L;
    private static final long DONOR_ID = 20L;
    private static final long RECEIVER_ID = 10L;
    private static final long ADMIN_ID = 1L;

    @Mock
    RequestRepository requestRepository;

    @Mock
    NotificationService notificationService;

    @Mock
    ConfigService configService;

    @Mock
    ItemService itemService;

    @Mock
    ItemQueryService itemQueryService;

    @Mock
    UserService userService;

    @Mock
    ReputationService reputationService;

    @Mock
    ActivityLogService activityLogService;

    RequestService requestService;

    @BeforeEach
    void setUp() {
        requestService = new RequestService(
                requestRepository,
                notificationService,
                configService,
                itemService,
                itemQueryService,
                userService,
                reputationService,
                null,
                null,
                null,
                null,
                activityLogService);
    }

    private Request disputedSwapRequest() {
        Request request = new Request(ITEM_ID, RECEIVER_ID, OFFERED_ITEM_ID, "SWAP");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", RequestStatus.DISPUTED);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        org.mockito.Mockito.lenient()
                .when(itemQueryService.getSummary(ITEM_ID))
                .thenReturn(new ItemSummary(ITEM_ID, DONOR_ID, null, null));
        return request;
    }

    private static List<ItemDisputeOutcome> outcomes(Choice main, Choice offered) {
        return List.of(new ItemDisputeOutcome(ITEM_ID, main), new ItemDisputeOutcome(OFFERED_ITEM_ID, offered));
    }

    @Test
    @DisplayName("Chấp nhận khiếu nại, mở lại cả hai món: Request CANCELLED, nhả cả hai món, thông báo cả hai bên")
    void resolveDisputedRequest_whenUpheldAndBothReopened_cancelsReleasesBothItemsAndNotifiesBoth() {
        // given
        Request request = disputedSwapRequest();

        // when
        boolean resolved = requestService.resolveDisputedRequest(
                REQUEST_ID, true, ADMIN_ID, outcomes(Choice.REOPEN, Choice.REOPEN));

        // then
        assertThat(resolved).isTrue();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(request.getCancelReason()).startsWith("[Hệ thống] ");
        verify(itemService).releaseIfReservedAfterCancel(eq(ITEM_ID), any());
        verify(itemService).releaseIfReservedAfterCancel(eq(OFFERED_ITEM_ID), any());
        verify(itemService, never()).hideByAdmin(org.mockito.ArgumentMatchers.anyLong());
        verify(itemService, never()).markTraded(org.mockito.ArgumentMatchers.anyLong());
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Chủ bài vi phạm: món chính Ẩn, món đổi Mở lại, ẩn bằng cờ của Admin")
    void resolveDisputedRequest_whenDonorSideHidden_hidesMainItemByAdminAndReopensOffered() {
        // given
        disputedSwapRequest();

        // when
        requestService.resolveDisputedRequest(REQUEST_ID, true, ADMIN_ID, outcomes(Choice.HIDE, Choice.REOPEN));

        // then
        verify(itemService).releaseIfReservedAfterCancel(eq(ITEM_ID), any());
        verify(itemService).hideByAdmin(ITEM_ID);
        verify(itemService, never()).hideByAdmin(OFFERED_ITEM_ID);
        verify(itemService, never()).hide(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("Người nhận vi phạm: món đổi của người nhận cũng bị ẩn (trước đây không), món chính mở lại")
    void resolveDisputedRequest_whenReceiverSideHidden_hidesOfferedItemToo() {
        // given
        disputedSwapRequest();

        // when
        requestService.resolveDisputedRequest(REQUEST_ID, true, ADMIN_ID, outcomes(Choice.REOPEN, Choice.HIDE));

        // then
        verify(itemService).hideByAdmin(OFFERED_ITEM_ID);
        verify(itemService, never()).hideByAdmin(ITEM_ID);
    }

    @Test
    @DisplayName("Đã đổi chủ: ẩn bài, ghi lý do ẩn riêng và báo hai bên món nằm ở chỗ người giữ")
    void resolveDisputedRequest_whenTransferred_hidesItemLogsReasonAndNotifiesHolder() {
        // given
        disputedSwapRequest();
        when(userService.findContact(RECEIVER_ID))
                .thenReturn(new ContactInfo(RECEIVER_ID, "Bình", "binh@test.local", "0900000001"));

        // when
        requestService.resolveDisputedRequest(REQUEST_ID, true, ADMIN_ID, outcomes(Choice.TRANSFERRED, Choice.REOPEN));

        // then
        verify(itemService).hideByAdmin(ITEM_ID);
        ArgumentCaptor<String> logNote = ArgumentCaptor.forClass(String.class);
        verify(activityLogService)
                .log(
                        eq("ADMIN_ITEM_HIDDEN"),
                        eq(ADMIN_ID),
                        eq("ITEM"),
                        eq(ITEM_ID),
                        org.mockito.ArgumentMatchers.<java.util.Map<String, Object>>any(),
                        logNote.capture());
        assertThat(logNote.getValue()).isEqualTo("Món đã được trao trong một giao dịch bị huỷ");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("TRANSACTION_CANCELLED"), anyString(), body.capture(), anyString());
        assertThat(body.getValue()).contains("đã nằm ở chỗ Bình. Hai bên liên hệ để hoàn trả nếu cần.");
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("TRANSACTION_CANCELLED"), anyString(), anyString(), anyString());
    }

    private String transferredBodyForShipping(boolean leg1Received, boolean leg2Received) {
        Request request = disputedSwapRequest();
        ReflectionTestUtils.setField(request, "deliveryMethod", "SHIPPING");
        if (leg1Received) {
            ReflectionTestUtils.setField(request, "receiverHandoverAt", java.time.Instant.now());
        }
        if (leg2Received) {
            ReflectionTestUtils.setField(request, "donorHandoverAt", java.time.Instant.now());
        }
        when(userService.findContact(RECEIVER_ID))
                .thenReturn(new ContactInfo(RECEIVER_ID, "Bình", "binh@test.local", "0900000001"));
        org.mockito.Mockito.lenient()
                .when(userService.findContact(DONOR_ID))
                .thenReturn(new ContactInfo(DONOR_ID, "An", "an@test.local", "0900000002"));

        requestService.resolveDisputedRequest(
                REQUEST_ID, true, ADMIN_ID, outcomes(Choice.TRANSFERRED, Choice.TRANSFERRED));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("TRANSACTION_CANCELLED"), anyString(), body.capture(), anyString());
        return body.getValue();
    }

    @Test
    @DisplayName("Giao hàng, chưa chiều nào nhận: món chính còn ở chỗ chủ bài, món đổi còn ở chỗ người nhận")
    void resolveDisputedRequest_whenShippingAndNoLegReceived_namesTheSenderAsHolderOfEachItem() {
        // when
        String body = transferredBodyForShipping(false, false);

        // then: câu của món chính nói chủ bài, câu của món đổi nói người nhận
        assertThat(body).contains("đã nằm ở chỗ An.").contains("đã nằm ở chỗ Bình.");
        assertThat(body.indexOf("ở chỗ An.")).isLessThan(body.indexOf("ở chỗ Bình."));
    }

    @Test
    @DisplayName("Giao hàng, cả hai chiều đã nhận: món chính ở chỗ người nhận, món đổi ở chỗ chủ bài")
    void resolveDisputedRequest_whenShippingAndBothLegsReceived_namesTheReceiverOfEachLeg() {
        // when
        String body = transferredBodyForShipping(true, true);

        // then
        assertThat(body.indexOf("ở chỗ Bình.")).isLessThan(body.indexOf("ở chỗ An."));
    }

    @Test
    @DisplayName("Giao hàng, chỉ chiều 1 đã nhận: cả hai món cùng nằm ở chỗ người nhận")
    void resolveDisputedRequest_whenShippingAndOnlyFirstLegReceived_bothItemsAtReceiver() {
        // when
        String body = transferredBodyForShipping(true, false);

        // then
        assertThat(body).doesNotContain("ở chỗ An.");
        assertThat(body.split("ở chỗ Bình\\.", -1)).hasSize(3);
    }

    @Test
    @DisplayName("Chấp nhận mà thiếu lựa chọn cho một món thì báo lỗi và không đổi gì")
    void resolveDisputedRequest_whenOutcomeMissingForAnItem_throwsOutcomesRequired() {
        // given
        Request request = disputedSwapRequest();
        List<ItemDisputeOutcome> onlyMain = List.of(new ItemDisputeOutcome(ITEM_ID, Choice.REOPEN));

        // when / then
        assertThatThrownBy(() -> requestService.resolveDisputedRequest(REQUEST_ID, true, ADMIN_ID, onlyMain))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_ITEM_OUTCOMES_REQUIRED));
        assertThat(request.getStatus()).isEqualTo(RequestStatus.DISPUTED);
        verify(itemService, never()).releaseIfReservedAfterCancel(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    @DisplayName("Chấp nhận mà không gửi danh sách lựa chọn hoặc gửi món lạ thì báo lỗi")
    void resolveDisputedRequest_whenOutcomesNullOrUnknownItem_throwsOutcomesRequired() {
        // given
        disputedSwapRequest();
        List<ItemDisputeOutcome> unknown =
                List.of(new ItemDisputeOutcome(ITEM_ID, Choice.REOPEN), new ItemDisputeOutcome(999L, Choice.REOPEN));

        // when / then
        assertThatThrownBy(() -> requestService.resolveDisputedRequest(REQUEST_ID, true, ADMIN_ID, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> requestService.resolveDisputedRequest(REQUEST_ID, true, ADMIN_ID, unknown))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Bác bỏ khiếu nại: Request COMPLETED, hai món TRADED, cộng điểm tích luỹ, không cần chọn từng món")
    void resolveDisputedRequest_whenDismissed_completesMarksTradedAndAwardsPoints() {
        // given
        Request request = disputedSwapRequest();

        // when
        boolean resolved = requestService.resolveDisputedRequest(REQUEST_ID, false, ADMIN_ID, null);

        // then
        assertThat(resolved).isTrue();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(request.getCompletedAt()).isNotNull();
        verify(itemService).markTraded(ITEM_ID);
        verify(itemService).markTraded(OFFERED_ITEM_ID);
        verify(reputationService).onCompleted(REQUEST_ID);
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("TRANSACTION_COMPLETED"), anyString(), anyString(), anyString());
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("TRANSACTION_COMPLETED"), anyString(), anyString(), anyString());
    }

    private Request disputedAfterRejectedCancel(com.shareloop.request.entity.CancelRequestCode code) {
        Request request = disputedSwapRequest();
        ReflectionTestUtils.setField(request, "cancelRequestedBy", DONOR_ID);
        ReflectionTestUtils.setField(request, "cancelRequestedAt", java.time.Instant.now());
        ReflectionTestUtils.setField(request, "cancelRequestCode", code);
        return request;
    }

    @Test
    @DisplayName(
            "Bác khiếu nại đề nghị huỷ không tố ai: giao dịch quay lại LOGISTICS_CONFIRMED, xoá đề nghị, không hoàn tất")
    void resolveDisputedRequest_whenDismissedAndCancelWasBusy_resumesTransaction() {
        // given
        Request request = disputedAfterRejectedCancel(com.shareloop.request.entity.CancelRequestCode.BUSY);

        // when
        boolean resolved = requestService.resolveDisputedRequest(REQUEST_ID, false, ADMIN_ID, null);

        // then
        assertThat(resolved).isTrue();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
        assertThat(request.getCancelRequestedBy()).isNull();
        verify(itemService, never()).markTraded(org.mockito.ArgumentMatchers.anyLong());
        verify(reputationService, never()).onCompleted(org.mockito.ArgumentMatchers.anyLong());
        verify(notificationService)
                .notify(eq(RECEIVER_ID), eq("TRANSACTION_RESUMED"), anyString(), anyString(), anyString());
        verify(notificationService)
                .notify(eq(DONOR_ID), eq("TRANSACTION_RESUMED"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Bác khiếu nại đề nghị huỷ không tố ai mà đã có bên bấm hoàn tất: quay lại AWAITING_HANDOVER")
    void resolveDisputedRequest_whenDismissedAndSomeoneAlreadyConfirmed_resumesAsAwaitingHandover() {
        // given
        Request request = disputedAfterRejectedCancel(com.shareloop.request.entity.CancelRequestCode.OTHER);
        ReflectionTestUtils.setField(request, "receiverHandoverAt", java.time.Instant.now());

        // when
        requestService.resolveDisputedRequest(REQUEST_ID, false, ADMIN_ID, null);

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_HANDOVER);
    }

    @Test
    @DisplayName("Bác khiếu nại đề nghị huỷ có tố bên kia: giữ luật cũ, công nhận hoàn tất")
    void resolveDisputedRequest_whenDismissedAndCancelAccusedCounterpart_stillCompletes() {
        // given
        Request request =
                disputedAfterRejectedCancel(com.shareloop.request.entity.CancelRequestCode.COUNTERPART_NO_SHOW);

        // when
        requestService.resolveDisputedRequest(REQUEST_ID, false, ADMIN_ID, null);

        // then
        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
    }

    @Test
    @DisplayName("isCancelDisputeWithoutAccusation: đúng với bận, hết nhu cầu, khác; sai với tố bên kia")
    void isCancelDisputeWithoutAccusation_dependsOnTheReasonCode() {
        // given
        Request busy = disputedAfterRejectedCancel(com.shareloop.request.entity.CancelRequestCode.NO_LONGER_NEEDED);

        // when and then
        assertThat(requestService.isCancelDisputeWithoutAccusation(REQUEST_ID)).isTrue();
        ReflectionTestUtils.setField(
                busy, "cancelRequestCode", com.shareloop.request.entity.CancelRequestCode.CANNOT_CONTACT);
        assertThat(requestService.isCancelDisputeWithoutAccusation(REQUEST_ID)).isFalse();
    }

    @Test
    @DisplayName("Giao dịch đã COMPLETED (khiếu nại mở sau hoàn tất) thì không bị đổi gì và không cần lựa chọn món")
    void resolveDisputedRequest_whenNotDisputed_doesNothing() {
        // given
        Request request = new Request(ITEM_ID, RECEIVER_ID, null, "GIVE");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", RequestStatus.COMPLETED);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        // when
        boolean resolved = requestService.resolveDisputedRequest(REQUEST_ID, true, ADMIN_ID, null);

        // then
        assertThat(resolved).isFalse();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        verify(itemService, never()).releaseIfReservedAfterCancel(org.mockito.ArgumentMatchers.anyLong(), any());
        verify(notificationService, never()).notify(any(Long.class), any(), any(), any(), any());
    }
}
