package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.ConfirmLogisticsRequest;
import com.shareloop.request.dto.CreateRequest;
import com.shareloop.request.dto.HandoverConfirmRequest;
import com.shareloop.request.dto.RequestContactResponse;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.ContactInfo;
import com.shareloop.user.service.UserService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

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
    JdbcClient jdbcClient;

    @Spy
    @InjectMocks
    RequestService requestService;

    @BeforeEach
    void setUp() {
        lenient().when(configService.getInt(ConfigKey.MAX_PENDING_REQUESTS)).thenReturn(5);
    }

    @Test
    @DisplayName("Ném lỗi khi đã có 5 request PENDING cùng lúc (BR-U02)")
    void createRequest_whenPendingLimitExceeded_throwsBusinessException() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(5L);

        assertThatThrownBy(() -> requestService.createRequest(1L, 10L, new CreateRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_LIMIT_EXCEEDED);
                });
    }

    @Test
    @DisplayName("Ném lỗi khi đã có request active cho cùng bài (BR-R02)")
    void createRequest_whenAlreadyExistsActiveRequest_throwsBusinessException() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(1L);
        when(requestRepository.existsByItemIdAndReceiverIdAndStatusIn(eq(1L), eq(10L), anySet()))
                .thenReturn(true);

        assertThatThrownBy(() -> requestService.createRequest(1L, 10L, new CreateRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_ALREADY_EXISTS);
                });
    }

    @Test
    @DisplayName("Ném lỗi khi tự gửi yêu cầu cho bài của chính mình (BR-R01)")
    void createRequest_whenSelfOffer_throwsBusinessException() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(0L);
        when(requestRepository.existsByItemIdAndReceiverIdAndStatusIn(eq(1L), eq(10L), anySet()))
                .thenReturn(false);

        // Giả lập item có donor_id = 10L (trùng receiverId)
        doReturn(Optional.of(new RequestService.ItemSnapshot(10L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.createRequest(1L, 10L, new CreateRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_SELF_OFFER_NOT_ALLOWED);
                });
    }

    @Test
    @DisplayName("Ném lỗi khi bài đăng chưa được duyệt (không ở trạng thái APPROVED)")
    void createRequest_whenItemNotApproved_throwsBusinessException() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(0L);
        when(requestRepository.existsByItemIdAndReceiverIdAndStatusIn(eq(1L), eq(10L), anySet()))
                .thenReturn(false);

        doReturn(Optional.of(new RequestService.ItemSnapshot(99L, "GIVE", "PENDING_REVIEW")))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.createRequest(1L, 10L, new CreateRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_ITEM_NOT_AVAILABLE);
                });
    }

    @Test
    @DisplayName("Ném lỗi khi bài Trao đổi nhưng không chọn món đề nghị (BR-S02)")
    void createRequest_whenSwapWithoutOfferedItem_throwsBusinessException() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(0L);
        when(requestRepository.existsByItemIdAndReceiverIdAndStatusIn(eq(1L), eq(10L), anySet()))
                .thenReturn(false);

        doReturn(Optional.of(new RequestService.ItemSnapshot(99L, "SWAP", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.createRequest(1L, 10L, new CreateRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_SWAP_OFFERED_ITEM_REQUIRED);
                });
    }

    @Test
    @DisplayName("Tạo yêu cầu GIVE thành công, lưu DB và trả về DTO PENDING")
    void createRequest_whenGiveSuccess_savesAndReturnsResponse() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(0L);
        when(requestRepository.existsByItemIdAndReceiverIdAndStatusIn(eq(1L), eq(10L), anySet()))
                .thenReturn(false);

        doReturn(Optional.of(new RequestService.ItemSnapshot(99L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> {
            Request toSave = invocation.getArgument(0);
            return toSave;
        });

        RequestResponse response = requestService.createRequest(1L, 10L, new CreateRequest(null));

        assertThat(response).isNotNull();
        assertThat(response.itemId()).isEqualTo(1L);
        assertThat(response.receiverId()).isEqualTo(10L);
        assertThat(response.type()).isEqualTo("GIVE");
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Người gửi tự huỷ yêu cầu thành công khi còn PENDING (BR-R04)")
    void cancelRequest_whenSuccess_cancelsAndReturnsResponse() {
        Request request = new Request(1L, 10L, null, "GIVE");

        when(requestRepository.findById(55L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        RequestResponse response = requestService.cancelRequest(55L, 10L);

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(notificationService).notify(eq(20L), eq("REQUEST_CANCELLED"), any(), any(), eq("/items/1"));
    }

    @Test
    @DisplayName("Ném lỗi FORBIDDEN khi người khác cố tình huỷ yêu cầu không phải của mình")
    void cancelRequest_whenNotReceiver_throwsForbidden() {
        Request request = new Request(1L, 10L, null, "GIVE");
        when(requestRepository.findById(55L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> requestService.cancelRequest(55L, 999L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_FORBIDDEN);
                });
    }

    @Test
    @DisplayName(
            "Chủ bài chọn người nhận thành công: chuyển RESERVED, reject các request khác và giữ chỗ bài (FR-66, BR-D01)")
    void selectReceiver_whenSuccess_reservesRequestAndItemAndRejectsOthers() {
        Request selectedRequest = new Request(1L, 10L, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(selectedRequest, "id", 101L);

        Request otherRequest = new Request(1L, 11L, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(otherRequest, "id", 102L);

        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        when(requestRepository.findById(101L)).thenReturn(Optional.of(selectedRequest));
        when(requestRepository.findByItemIdAndStatus(1L, RequestStatus.PENDING))
                .thenReturn(List.of(selectedRequest, otherRequest));
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));

        RequestResponse response = requestService.selectReceiver(101L, 20L);

        assertThat(response.status()).isEqualTo("RESERVED");
        assertThat(selectedRequest.getStatus()).isEqualTo(RequestStatus.RESERVED);
        assertThat(selectedRequest.getReservedAt()).isNotNull();

        assertThat(otherRequest.getStatus()).isEqualTo(RequestStatus.REJECTED);
        org.mockito.Mockito.verify(itemService).reserve(1L);
        org.mockito.Mockito.verify(notificationService).notify(eq(10L), eq("REQUEST_ACCEPTED"), any(), any(), any());
        org.mockito.Mockito.verify(notificationService).notify(eq(11L), eq("REQUEST_REJECTED"), any(), any(), any());
    }

    @Test
    @DisplayName("Ném lỗi FORBIDDEN khi người khác (không phải chủ bài) cố tình chọn người nhận")
    void selectReceiver_whenNotOwner_throwsForbidden() {
        Request request = new Request(1L, 10L, null, "GIVE");
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));

        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.selectReceiver(101L, 999L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_FORBIDDEN);
                });
    }

    @Test
    @DisplayName("Ném lỗi NOT_FOUND khi chọn request không tồn tại")
    void selectReceiver_whenNotFound_throwsNotFound() {
        when(requestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestService.selectReceiver(999L, 20L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("Ném lỗi INVALID_STATE_TRANSITION khi request không còn ở trạng thái PENDING")
    void selectReceiver_whenNotPending_throwsInvalidStateTransition() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.cancel("Đã huỷ trước");

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.selectReceiver(101L, 20L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
                });
    }

    @Test
    @DisplayName("getDetail: Thành công khi người gọi là donor hoặc receiver")
    void getDetail_whenValidUser_returnsDetail() {
        Request request = new Request(1L, 10L, null, "GIVE");
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        RequestResponse resReceiver = requestService.getDetail(101L, 10L);
        assertThat(resReceiver).isNotNull();
        assertThat(resReceiver.receiverId()).isEqualTo(10L);

        RequestResponse resDonor = requestService.getDetail(101L, 20L);
        assertThat(resDonor).isNotNull();
        assertThat(resDonor.itemId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getDetail: Ném lỗi FORBIDDEN khi người gọi là người ngoài")
    void getDetail_whenOutsider_throwsForbidden() {
        Request request = new Request(1L, 10L, null, "GIVE");
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.getDetail(101L, 999L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_FORBIDDEN);
                });
    }

    @Test
    @DisplayName("updateSchedule: Thành công khi đề xuất lịch từ trạng thái RESERVED")
    void updateSchedule_whenReserved_transitionsToAwaitingLogistics() {
        Request request = new Request(1L, 10L, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "id", 101L);
        request.select(Instant.now()); // RESERVED
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        Instant meetingTime = Instant.now().plus(2, ChronoUnit.DAYS);
        ScheduleRequest dto = new ScheduleRequest("IN_PERSON", "Cổng trường FPT", meetingTime, null);

        RequestResponse response = requestService.updateSchedule(101L, 20L, dto); // donor đề xuất

        assertThat(response.status()).isEqualTo("AWAITING_LOGISTICS");
        assertThat(response.deliveryMethod()).isEqualTo("IN_PERSON");
        assertThat(response.meetingPlace()).isEqualTo("Cổng trường FPT");
        assertThat(response.meetingTime()).isEqualTo(meetingTime);
        assertThat(response.donorScheduleOkAt()).isNotNull();
        assertThat(response.receiverScheduleOkAt()).isNull();
        verify(notificationService).notify(eq(10L), eq("SCHEDULE_UPDATED"), any(), any(), eq("/requests/101"));
    }

    @Test
    @DisplayName("updateSchedule: Receiver đề xuất lịch -> thông báo SCHEDULE_UPDATED cho Donor")
    void updateSchedule_whenReceiverUpdates_notifiesDonor() {
        Request request = new Request(1L, 10L, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "id", 101L);
        request.select(Instant.now()); // RESERVED
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        Instant meetingTime = Instant.now().plus(2, ChronoUnit.DAYS);
        ScheduleRequest dto = new ScheduleRequest("IN_PERSON", "Cổng trường FPT", meetingTime, null);

        RequestResponse response = requestService.updateSchedule(101L, 10L, dto); // receiver đề xuất

        assertThat(response.status()).isEqualTo("AWAITING_LOGISTICS");
        assertThat(response.receiverScheduleOkAt()).isNotNull();
        assertThat(response.donorScheduleOkAt()).isNull();
        verify(notificationService).notify(eq(20L), eq("SCHEDULE_UPDATED"), any(), any(), eq("/requests/101"));
    }

    @Test
    @DisplayName("updateSchedule: Ném lỗi SCHEDULE_PAST_TIME khi thời gian hẹn ở quá khứ")
    void updateSchedule_whenPastTime_throwsPastTime() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        Instant pastTime = Instant.now().minus(1, ChronoUnit.HOURS);
        ScheduleRequest dto = new ScheduleRequest("IN_PERSON", "Cổng trường", pastTime, null);

        assertThatThrownBy(() -> requestService.updateSchedule(101L, 20L, dto))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_SCHEDULE_PAST_TIME);
                });
    }

    @Test
    @DisplayName("updateSchedule: Ném lỗi SCHEDULE_LOCKED khi đã ở LOGISTICS_CONFIRMED")
    void updateSchedule_whenAlreadyConfirmed_throwsScheduleLocked() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());
        request.proposeSchedule(
                "IN_PERSON", "Cổng trường", Instant.now().plus(1, ChronoUnit.DAYS), true, false, Instant.now());
        request.confirmLogistics(false, false, Instant.now()); // sang LOGISTICS_CONFIRMED

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        Instant futureTime = Instant.now().plus(2, ChronoUnit.DAYS);
        ScheduleRequest dto = new ScheduleRequest("IN_PERSON", "Địa điểm mới", futureTime, null);

        assertThatThrownBy(() -> requestService.updateSchedule(101L, 20L, dto))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_SCHEDULE_LOCKED);
                });
    }

    @Test
    @DisplayName("confirmLogistics: Giao trực tiếp (IN_PERSON), khi bên thứ 2 xác nhận thì chuyển LOGISTICS_CONFIRMED")
    void confirmLogistics_inPerson_bothConfirm_transitionsToLogisticsConfirmed() {
        Request request = new Request(1L, 10L, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "id", 101L);
        request.select(Instant.now());
        Instant meetingTime = Instant.now().plus(2, ChronoUnit.DAYS);
        request.proposeSchedule("IN_PERSON", "Cổng trường", meetingTime, true, false, Instant.now());

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        // Receiver gọi xác nhận lịch
        RequestResponse response = requestService.confirmLogistics(101L, 10L, new ConfirmLogisticsRequest(null));

        assertThat(response.status()).isEqualTo("LOGISTICS_CONFIRMED");
        assertThat(response.receiverScheduleOkAt()).isNotNull();
        assertThat(response.donorScheduleOkAt()).isNotNull();
        assertThat(response.contactRevealedAt()).isNotNull();
        verify(notificationService).notify(eq(20L), eq("LOGISTICS_CONFIRMED"), any(), any(), eq("/requests/101"));
    }

    @Test
    @DisplayName(
            "confirmLogistics: Khi mới 1 bên xác nhận (chưa LOGISTICS_CONFIRMED) -> không bắn thông báo LOGISTICS_CONFIRMED")
    void confirmLogistics_whenOnlyOneSideConfirms_doesNotNotifyLogisticsConfirmed() {
        Request request = new Request(1L, 10L, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "id", 101L);
        request.select(Instant.now());
        Instant meetingTime = Instant.now().plus(2, ChronoUnit.DAYS);
        // Receiver đề xuất lịch nhưng chưa xác nhận (cả 2 null)
        request.proposeSchedule("IN_PERSON", "Cổng trường", meetingTime, false, false, Instant.now());

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        // Receiver gọi xác nhận lịch (chỉ receiver ok, donor chưa ok)
        RequestResponse response = requestService.confirmLogistics(101L, 10L, new ConfirmLogisticsRequest(null));

        assertThat(response.status()).isEqualTo("AWAITING_LOGISTICS");
        verify(notificationService, never()).notify(anyLong(), eq("LOGISTICS_CONFIRMED"), any(), any(), any());
    }

    @Test
    @DisplayName("confirmLogistics: Giao hàng (SHIPPING), ném WAIVER_REQUIRED nếu chưa đồng ý cam kết")
    void confirmLogistics_shipping_withoutWaiver_throwsWaiverRequired() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());
        Instant meetingTime = Instant.now().plus(2, ChronoUnit.DAYS);
        request.proposeSchedule("SHIPPING", "123 Đường XYZ", meetingTime, true, true, Instant.now());

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        // Receiver không đồng ý waiver
        assertThatThrownBy(() -> requestService.confirmLogistics(101L, 10L, new ConfirmLogisticsRequest(false)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_WAIVER_REQUIRED);
                });
    }

    @Test
    @DisplayName("confirmLogistics: Giao hàng (SHIPPING), cả hai bên cam kết waiver thì sang LOGISTICS_CONFIRMED")
    void confirmLogistics_shipping_bothWaive_transitionsToLogisticsConfirmed() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());
        Instant meetingTime = Instant.now().plus(2, ChronoUnit.DAYS);
        // Donor đề xuất và agreeToWaiver = true
        request.proposeSchedule("SHIPPING", "123 Đường XYZ", meetingTime, true, true, Instant.now());

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        // Receiver gọi confirm kèm agreeToWaiver = true
        RequestResponse response = requestService.confirmLogistics(101L, 10L, new ConfirmLogisticsRequest(true));

        assertThat(response.status()).isEqualTo("LOGISTICS_CONFIRMED");
        assertThat(response.donorWaiverAt()).isNotNull();
        assertThat(response.receiverWaiverAt()).isNotNull();
        assertThat(response.contactRevealedAt()).isNotNull();
    }

    @Test
    @DisplayName("getContactInfo: Chủ bài (donor) xem thông tin người nhận (receiver) khi đã chốt lịch")
    void getContactInfo_whenDonorCalls_returnsReceiverContact() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());
        request.proposeSchedule(
                "IN_PERSON", "Cổng trường", Instant.now().plus(2, ChronoUnit.DAYS), true, false, Instant.now());
        request.confirmLogistics(false, false, Instant.now()); // sang LOGISTICS_CONFIRMED, contactRevealedAt != null

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(userService.findContact(10L))
                .thenReturn(new ContactInfo(10L, "Người Nhận", "receiver@example.com", "0901234567"));

        RequestContactResponse response = requestService.getContactInfo(101L, 20L); // donor gọi

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.fullName()).isEqualTo("Người Nhận");
        assertThat(response.email()).isEqualTo("receiver@example.com");
        assertThat(response.phone()).isEqualTo("0901234567");
    }

    @Test
    @DisplayName("getContactInfo: Người nhận (receiver) xem thông tin chủ bài (donor) khi đã chốt lịch")
    void getContactInfo_whenReceiverCalls_returnsDonorContact() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());
        request.proposeSchedule(
                "IN_PERSON", "Cổng trường", Instant.now().plus(2, ChronoUnit.DAYS), true, false, Instant.now());
        request.confirmLogistics(false, false, Instant.now()); // sang LOGISTICS_CONFIRMED

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(userService.findContact(20L))
                .thenReturn(new ContactInfo(20L, "Chủ Bài", "donor@example.com", "0987654321"));

        RequestContactResponse response = requestService.getContactInfo(101L, 10L); // receiver gọi

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(20L);
        assertThat(response.fullName()).isEqualTo("Chủ Bài");
        assertThat(response.email()).isEqualTo("donor@example.com");
        assertThat(response.phone()).isEqualTo("0987654321");
    }

    @Test
    @DisplayName("getContactInfo: Ném lỗi CONTACT_NOT_REVEALED khi chưa chốt lịch")
    void getContactInfo_whenBeforeLogisticsConfirmed_throwsContactNotRevealed() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now()); // RESERVED, contactRevealedAt is null

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.getContactInfo(101L, 20L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_CONTACT_NOT_REVEALED);
                });
    }

    @Test
    @DisplayName("getContactInfo: Ném lỗi FORBIDDEN khi người ngoài yêu cầu xem thông tin")
    void getContactInfo_whenOutsider_throwsForbidden() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.getContactInfo(101L, 999L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_FORBIDDEN);
                });
    }

    @Test
    @DisplayName("getContactInfo: Ném lỗi NOT_FOUND khi yêu cầu không tồn tại")
    void getContactInfo_whenNotFound_throwsNotFound() {
        when(requestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestService.getContactInfo(999L, 20L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("confirmHandover: Donor xác nhận trước thì chuyển sang AWAITING_HANDOVER và thông báo cho Receiver")
    void confirmHandover_whenDonorFirst_transitionsToAwaitingHandover() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());
        request.proposeSchedule("IN_PERSON", "FPT", Instant.now().plus(1, ChronoUnit.DAYS), true, false, Instant.now());
        request.confirmLogistics(false, false, Instant.now());
        assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        RequestResponse response = requestService.confirmHandover(101L, 20L, new HandoverConfirmRequest(null, null));

        assertThat(response.status()).isEqualTo("AWAITING_HANDOVER");
        assertThat(request.getDonorHandoverAt()).isNotNull();
        assertThat(request.getReceiverHandoverAt()).isNull();
        verify(notificationService).notify(eq(10L), eq("HANDOVER_CONFIRMED"), any(), any(), any());
    }

    @Test
    @DisplayName("confirmHandover: Receiver xác nhận trước thì chuyển sang AWAITING_HANDOVER và thông báo cho Donor")
    void confirmHandover_whenReceiverFirst_transitionsToAwaitingHandover_notifiesDonor() {
        Request request = new Request(1L, 10L, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "id", 101L);
        request.select(Instant.now());
        request.proposeSchedule("IN_PERSON", "FPT", Instant.now().plus(1, ChronoUnit.DAYS), true, false, Instant.now());
        request.confirmLogistics(false, false, Instant.now());
        assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        RequestResponse response = requestService.confirmHandover(101L, 10L, new HandoverConfirmRequest(true, null));

        assertThat(response.status()).isEqualTo("AWAITING_HANDOVER");
        assertThat(request.getReceiverHandoverAt()).isNotNull();
        assertThat(request.getDonorHandoverAt()).isNull();
        verify(notificationService).notify(eq(20L), eq("HANDOVER_CONFIRMED"), any(), any(), eq("/requests/101"));
    }

    @Test
    @DisplayName(
            "confirmHandover: Receiver xác nhận sau Donor với đúng mô tả -> COMPLETED, gọi ItemService và ReputationService")
    void confirmHandover_whenReceiverConfirmsAfterDonor_transitionsToCompleted() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());
        request.proposeSchedule("IN_PERSON", "FPT", Instant.now().plus(1, ChronoUnit.DAYS), true, false, Instant.now());
        request.confirmLogistics(false, false, Instant.now());
        request.confirmHandover(true, null, Instant.now());
        assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_HANDOVER);

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        RequestResponse response = requestService.confirmHandover(101L, 10L, new HandoverConfirmRequest(true, null));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(request.getReceiverHandoverAt()).isNotNull();
        assertThat(request.getAsDescribed()).isTrue();
        assertThat(request.getCompletedAt()).isNotNull();
        verify(itemService).markTraded(1L);
        verify(reputationService).onCompleted(101L);
        verify(notificationService).notify(eq(10L), eq("TRANSACTION_COMPLETED"), any(), any(), any());
        verify(notificationService).notify(eq(20L), eq("TRANSACTION_COMPLETED"), any(), any(), any());
    }

    @Test
    @DisplayName("confirmHandover: Khi bài SWAP hoàn tất -> gọi markTraded cho cả hai món đồ")
    void confirmHandover_whenSwapCompleted_marksBothItemsTraded() {
        Request request = new Request(1L, 10L, 2L, "SWAP");
        request.select(Instant.now());
        request.proposeSchedule("IN_PERSON", "FPT", Instant.now().plus(1, ChronoUnit.DAYS), true, false, Instant.now());
        request.confirmLogistics(false, false, Instant.now());
        request.confirmHandover(true, null, Instant.now());

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "SWAP", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        RequestResponse response = requestService.confirmHandover(101L, 10L, new HandoverConfirmRequest(true, null));

        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(itemService).markTraded(1L);
        verify(itemService).markTraded(2L);
        verify(reputationService).onCompleted(101L);
    }

    @Test
    @DisplayName(
            "confirmHandover: Receiver chọn không đúng mô tả mà không có lý do -> ném lỗi REQUEST_NOT_AS_DESCRIBED_REASON_REQUIRED")
    void confirmHandover_whenReceiverSelectsNotAsDescribedWithoutReason_throwsError() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());
        request.proposeSchedule("IN_PERSON", "FPT", Instant.now().plus(1, ChronoUnit.DAYS), true, false, Instant.now());
        request.confirmLogistics(false, false, Instant.now());

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.confirmHandover(101L, 10L, new HandoverConfirmRequest(false, "   ")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_NOT_AS_DESCRIBED_REASON_REQUIRED);
                });
    }

    @Test
    @DisplayName("confirmHandover: Receiver không trả lời câu hỏi đúng mô tả -> ném lỗi REQUEST_AS_DESCRIBED_REQUIRED")
    void confirmHandover_whenReceiverMissingAsDescribed_throwsError() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());
        request.proposeSchedule("IN_PERSON", "FPT", Instant.now().plus(1, ChronoUnit.DAYS), true, false, Instant.now());
        request.confirmLogistics(false, false, Instant.now());

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.confirmHandover(101L, 10L, new HandoverConfirmRequest(null, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_AS_DESCRIBED_REQUIRED);
                });
    }

    @Test
    @DisplayName("confirmHandover: Người ngoài gọi handover -> ném lỗi REQUEST_FORBIDDEN")
    void confirmHandover_whenOutsider_throwsForbidden() {
        Request request = new Request(1L, 10L, null, "GIVE");
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.confirmHandover(101L, 999L, new HandoverConfirmRequest(true, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_FORBIDDEN);
                });
    }

    @Test
    @DisplayName("confirmHandover: Đã xác nhận trước đó rồi mà bấm lại -> ném lỗi REQUEST_HANDOVER_ALREADY_CONFIRMED")
    void confirmHandover_whenAlreadyConfirmed_throwsError() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());
        request.proposeSchedule("IN_PERSON", "FPT", Instant.now().plus(1, ChronoUnit.DAYS), true, false, Instant.now());
        request.confirmLogistics(false, false, Instant.now());
        request.confirmHandover(true, null, Instant.now());

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.confirmHandover(101L, 20L, new HandoverConfirmRequest(null, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_HANDOVER_ALREADY_CONFIRMED);
                });
    }

    @Test
    @DisplayName(
            "confirmHandover: Yêu cầu chưa chốt lịch (chưa đạt LOGISTICS_CONFIRMED) -> ném lỗi REQUEST_INVALID_STATE_TRANSITION")
    void confirmHandover_whenNotLogisticsConfirmed_throwsInvalidTransition() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now()); // RESERVED

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.confirmHandover(101L, 20L, new HandoverConfirmRequest(null, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
                });
    }

    @Test
    @DisplayName("listByItem: Trả kèm họ tên người xin lấy từ UserService.findContact")
    void listByItem_whenDonorCalls_returnsReceiverName() {
        // given
        Request request = new Request(1L, 10L, null, "GIVE");
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(requestRepository.findByItemIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(request));
        when(userService.findContact(10L))
                .thenReturn(new ContactInfo(10L, "Nguyễn Văn B", "receiver@example.com", "0901234567"));

        // when
        List<RequestResponse> responses = requestService.listByItem(1L, 20L); // donor gọi

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).receiverName()).isEqualTo("Nguyễn Văn B");
    }
}
