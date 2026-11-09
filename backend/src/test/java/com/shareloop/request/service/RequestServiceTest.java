package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.ItemCardDto;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.report.service.ReportService;
import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.CancelTransactionRequest;
import com.shareloop.request.dto.ConfirmLogisticsRequest;
import com.shareloop.request.dto.CreateRequest;
import com.shareloop.request.dto.HandoverConfirmRequest;
import com.shareloop.request.dto.RequestContactResponse;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.event.NotAsDescribedReportedEvent;
import com.shareloop.request.event.RequestNegotiationEndedEvent;
import com.shareloop.request.repository.RequestMediaRepository;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.ContactInfo;
import com.shareloop.user.service.UserService;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.service.CreditService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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

    // getDetail đọc thêm ảnh giao nhận; mock repository trả danh sách rỗng thay cho JdbcClient chưa stub câu SQL.
    @Mock
    RequestMediaRepository requestMediaRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    ReportService reportService;

    @Mock
    CreditService creditService;

    @Spy
    @InjectMocks
    RequestService requestService;

    @BeforeEach
    void setUp() {
        lenient().when(configService.getInt(ConfigKey.MAX_PENDING_REQUESTS)).thenReturn(5);
        lenient().when(configService.getInt(ConfigKey.POST_FEE)).thenReturn(5);
        lenient().when(reputationService.canRequest(anyLong())).thenReturn(true);
        lenient().when(userService.isLocked(anyLong())).thenReturn(false);
        lenient().when(userService.isAdmin(anyLong())).thenReturn(false);
    }

    @Test
    @DisplayName("Ném lỗi khi người gửi yêu cầu là quản trị viên (TK-13)")
    void createRequest_whenAdmin_throwsRequestAdminBlocked() {
        when(userService.isAdmin(10L)).thenReturn(true);

        assertThatThrownBy(() -> requestService.createRequest(1L, 10L, new CreateRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_ADMIN_BLOCKED);
                });
    }

    @Test
    @DisplayName("Ném lỗi khi người gửi yêu cầu có 0 sao uy tín (YC-02, UT-04, KC-05)")
    void createRequest_whenTrustStarsZero_throwsBusinessException() {
        when(reputationService.canRequest(10L)).thenReturn(false);

        assertThatThrownBy(() -> requestService.createRequest(1L, 10L, new CreateRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_REPUTATION_BLOCKED);
                });
    }

    @Test
    @DisplayName("Ném lỗi khi đã có 5 request PENDING cùng lúc (YC-03)")
    void createRequest_whenPendingLimitExceeded_throwsBusinessException() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(5L);

        assertThatThrownBy(() -> requestService.createRequest(1L, 10L, new CreateRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_LIMIT_EXCEEDED);
                });
    }

    @Test
    @DisplayName("Ném lỗi khi đã có request active cho cùng bài (YC-03)")
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
    @DisplayName("Ném lỗi khi tự gửi yêu cầu cho bài của chính mình (YC-01)")
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
    @DisplayName("Ném lỗi khi bài đăng bị ẩn (is_active = false)")
    void createRequest_whenItemNotActive_throwsBusinessException() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(0L);
        when(requestRepository.existsByItemIdAndReceiverIdAndStatusIn(eq(1L), eq(10L), anySet()))
                .thenReturn(false);

        doReturn(Optional.of(new RequestService.ItemSnapshot(99L, "GIVE", "APPROVED", false)))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.createRequest(1L, 10L, new CreateRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_ITEM_NOT_AVAILABLE);
                });
    }

    @Test
    @DisplayName("Ném lỗi khi chủ bài đăng đang bị khoá tài khoản")
    void createRequest_whenDonorLocked_throwsBusinessException() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(0L);
        when(requestRepository.existsByItemIdAndReceiverIdAndStatusIn(eq(1L), eq(10L), anySet()))
                .thenReturn(false);

        doReturn(Optional.of(new RequestService.ItemSnapshot(99L, "GIVE", "APPROVED", true)))
                .when(requestService)
                .findItemSnapshot(1L);
        when(userService.isLocked(99L)).thenReturn(true);

        assertThatThrownBy(() -> requestService.createRequest(1L, 10L, new CreateRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_ITEM_NOT_AVAILABLE);
                });
    }

    @Test
    @DisplayName("Ném lỗi khi chính người gửi yêu cầu bị khoá tài khoản")
    void createRequest_whenReceiverLocked_throwsBusinessException() {
        when(userService.isLocked(10L)).thenReturn(true);

        assertThatThrownBy(() -> requestService.createRequest(1L, 10L, new CreateRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_REPUTATION_BLOCKED);
                });
    }

    @Test
    @DisplayName("Tạo yêu cầu bài Trao đổi không chọn món đề nghị thành công (xin cho không, CD-01)")
    void createRequest_whenSwapWithoutOfferedItem_success() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(0L);
        when(requestRepository.existsByItemIdAndReceiverIdAndStatusIn(eq(1L), eq(10L), anySet()))
                .thenReturn(false);

        doReturn(Optional.of(new RequestService.ItemSnapshot(99L, "SWAP", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RequestResponse response = requestService.createRequest(1L, 10L, new CreateRequest(null));

        assertThat(response).isNotNull();
        assertThat(response.itemId()).isEqualTo(1L);
        assertThat(response.receiverId()).isEqualTo(10L);
        assertThat(response.offeredItemId()).isNull();
        assertThat(response.type()).isEqualTo("SWAP");
        assertThat(response.status()).isEqualTo("PENDING");
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
    @DisplayName("Tạo yêu cầu bài Cho có gửi kèm món đổi thành công (đề nghị trao đổi, CD-01)")
    void createRequest_whenGiveWithOfferedItem_success() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(0L);
        when(requestRepository.existsByItemIdAndReceiverIdAndStatusIn(eq(1L), eq(10L), anySet()))
                .thenReturn(false);

        doReturn(Optional.of(new RequestService.ItemSnapshot(99L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        doReturn(Optional.of(new RequestService.ItemSnapshot(10L, "GIVE", "APPROVED", true)))
                .when(requestService)
                .findItemSnapshot(5L);

        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RequestResponse response = requestService.createRequest(1L, 10L, new CreateRequest(5L));

        assertThat(response).isNotNull();
        assertThat(response.itemId()).isEqualTo(1L);
        assertThat(response.receiverId()).isEqualTo(10L);
        assertThat(response.offeredItemId()).isEqualTo(5L);
        assertThat(response.type()).isEqualTo("GIVE");
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Ném lỗi khi món đề nghị trùng với chính món đang yêu cầu (CD-01)")
    void createRequest_whenOfferedItemSameAsTargetItem_throwsBusinessException() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(0L);
        when(requestRepository.existsByItemIdAndReceiverIdAndStatusIn(eq(1L), eq(10L), anySet()))
                .thenReturn(false);

        doReturn(Optional.of(new RequestService.ItemSnapshot(99L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.createRequest(1L, 10L, new CreateRequest(1L)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_SELF_OFFER_NOT_ALLOWED);
                });
    }

    @Test
    @DisplayName("Ném lỗi khi món đề nghị trong bài Trao đổi không phải của người gửi")
    void createRequest_whenSwapWithOfferedItemNotOwned_throwsBusinessException() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(0L);
        when(requestRepository.existsByItemIdAndReceiverIdAndStatusIn(eq(1L), eq(10L), anySet()))
                .thenReturn(false);

        doReturn(Optional.of(new RequestService.ItemSnapshot(99L, "SWAP", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        doReturn(Optional.of(new RequestService.ItemSnapshot(88L, "SWAP", "APPROVED", true)))
                .when(requestService)
                .findItemSnapshot(5L);

        assertThatThrownBy(() -> requestService.createRequest(1L, 10L, new CreateRequest(5L)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_ITEM_NOT_AVAILABLE);
                });
    }

    @Test
    @DisplayName("Ném lỗi khi món đề nghị trong bài Trao đổi chưa được duyệt (không APPROVED)")
    void createRequest_whenSwapWithOfferedItemNotApproved_throwsBusinessException() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(0L);
        when(requestRepository.existsByItemIdAndReceiverIdAndStatusIn(eq(1L), eq(10L), anySet()))
                .thenReturn(false);

        doReturn(Optional.of(new RequestService.ItemSnapshot(99L, "SWAP", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        doReturn(Optional.of(new RequestService.ItemSnapshot(10L, "SWAP", "PENDING", true)))
                .when(requestService)
                .findItemSnapshot(5L);

        assertThatThrownBy(() -> requestService.createRequest(1L, 10L, new CreateRequest(5L)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_ITEM_NOT_AVAILABLE);
                });
    }

    @Test
    @DisplayName("Tạo yêu cầu SWAP thành công khi món đề nghị hợp lệ")
    void createRequest_whenSwapSuccess_savesAndReturnsResponse() {
        when(requestRepository.countByReceiverIdAndStatus(10L, RequestStatus.PENDING))
                .thenReturn(0L);
        when(requestRepository.existsByItemIdAndReceiverIdAndStatusIn(eq(1L), eq(10L), anySet()))
                .thenReturn(false);

        doReturn(Optional.of(new RequestService.ItemSnapshot(99L, "SWAP", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        doReturn(Optional.of(new RequestService.ItemSnapshot(10L, "SWAP", "APPROVED", true)))
                .when(requestService)
                .findItemSnapshot(5L);

        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RequestResponse response = requestService.createRequest(1L, 10L, new CreateRequest(5L));

        assertThat(response).isNotNull();
        assertThat(response.itemId()).isEqualTo(1L);
        assertThat(response.receiverId()).isEqualTo(10L);
        assertThat(response.offeredItemId()).isEqualTo(5L);
        assertThat(response.type()).isEqualTo("SWAP");
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Người gửi tự huỷ yêu cầu thành công khi còn PENDING (YC-05)")
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
    @DisplayName("Ném lỗi REQUEST_INVALID_STATE_TRANSITION khi huỷ yêu cầu không còn ở trạng thái PENDING (YC-05)")
    void cancelRequest_whenNotPending_throwsInvalidStateTransition() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.transitionTo(com.shareloop.request.entity.RequestStatus.RESERVED);
        when(requestRepository.findById(55L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> requestService.cancelRequest(55L, 10L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
                });
    }

    @Test
    @DisplayName("Chủ bài chọn người nhận thành công: chuyển RESERVED, reject các request khác và giữ chỗ bài (YC-07)")
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
        org.mockito.Mockito.verify(notificationService)
                .notify(eq(10L), eq("REQUEST_ACCEPTED"), any(), any(), eq("/requests/101"));
        org.mockito.Mockito.verify(notificationService)
                .notify(eq(11L), eq("REQUEST_REJECTED"), any(), any(), eq("/requests/102"));
    }

    @Test
    @DisplayName("Chủ bài chọn người nhận: tự huỷ Request khác dùng cùng món đề nghị (YC-07, YC-04)")
    void selectReceiver_whenSwapRequest_cancelsOtherRequestsWithSameOfferedItem() {
        Request selectedRequest = new Request(1L, 10L, 50L, "SWAP");
        org.springframework.test.util.ReflectionTestUtils.setField(selectedRequest, "id", 101L);

        Request otherSwapRequest = new Request(2L, 10L, 50L, "SWAP");
        org.springframework.test.util.ReflectionTestUtils.setField(otherSwapRequest, "id", 103L);

        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "SWAP", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        when(requestRepository.findById(101L)).thenReturn(Optional.of(selectedRequest));
        when(requestRepository.findByItemIdAndStatus(1L, RequestStatus.PENDING)).thenReturn(List.of(selectedRequest));
        when(requestRepository.findByOfferedItemIdAndStatus(50L, RequestStatus.PENDING))
                .thenReturn(List.of(selectedRequest, otherSwapRequest));
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));

        RequestResponse response = requestService.selectReceiver(101L, 20L);

        assertThat(response.status()).isEqualTo("RESERVED");
        assertThat(otherSwapRequest.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(otherSwapRequest.getCancelReason()).isNotNull();
        org.mockito.Mockito.verify(notificationService)
                .notify(eq(10L), eq("REQUEST_CANCELLED"), any(), any(), eq("/requests/103"));
    }

    @Test
    @DisplayName("Chọn yêu cầu SWAP thì giữ chỗ cả bài chính lẫn món mang đổi")
    void selectReceiver_whenSwapRequest_reservesBothItems() {
        // given
        Request selectedRequest = new Request(1L, 10L, 50L, "SWAP");
        org.springframework.test.util.ReflectionTestUtils.setField(selectedRequest, "id", 101L);
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "SWAP", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(requestRepository.findById(101L)).thenReturn(Optional.of(selectedRequest));
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));

        // when
        requestService.selectReceiver(101L, 20L);

        // then
        org.mockito.Mockito.verify(itemService).reserve(1L);
        org.mockito.Mockito.verify(itemService).reserve(50L);
    }

    @Test
    @DisplayName("Chọn yêu cầu GIVE thì chỉ giữ chỗ bài chính")
    void selectReceiver_whenGiveRequest_reservesOnlyMainItem() {
        // given
        Request selectedRequest = new Request(1L, 10L, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(selectedRequest, "id", 101L);
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(requestRepository.findById(101L)).thenReturn(Optional.of(selectedRequest));
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));

        // when
        requestService.selectReceiver(101L, 20L);

        // then
        org.mockito.Mockito.verify(itemService).reserve(1L);
        org.mockito.Mockito.verifyNoMoreInteractions(itemService);
    }

    @Test
    @DisplayName(
            "Chủ bài Cho chọn yêu cầu kèm món đổi: trừ phí 5 Credit, đổi sang SWAP và giữ chỗ cả 2 món (CD-02, CD-06)")
    void selectReceiver_whenGiveToSwap_chargesPostFeeAndConvertsBothTypesAndReservesBoth() {
        // given: bài GIVE, request có offeredItemId
        Request selectedRequest = new Request(1L, 10L, 50L, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(selectedRequest, "id", 101L);
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(requestRepository.findById(101L)).thenReturn(Optional.of(selectedRequest));
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));

        // when
        RequestResponse response = requestService.selectReceiver(101L, 20L);

        // then
        org.mockito.Mockito.verify(creditService).spend(20L, 5, CreditTxType.POST_FEE, 1L);
        org.mockito.Mockito.verify(itemService).convertOfferType(1L, OfferType.SWAP);
        assertThat(selectedRequest.getType()).isEqualTo("SWAP");
        assertThat(response.type()).isEqualTo("SWAP");
        org.mockito.Mockito.verify(itemService).reserve(1L);
        org.mockito.Mockito.verify(itemService).reserve(50L);
    }

    @Test
    @DisplayName(
            "Chủ bài Cho chọn yêu cầu kèm món đổi nhưng không đủ Credit: ném lỗi WALLET_INSUFFICIENT_BALANCE (CD-02)")
    void selectReceiver_whenGiveToSwap_insufficientBalance_throwsException() {
        // given: bài GIVE, request có offeredItemId
        Request selectedRequest = new Request(1L, 10L, 50L, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(selectedRequest, "id", 101L);
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(requestRepository.findById(101L)).thenReturn(Optional.of(selectedRequest));

        org.mockito.Mockito.doThrow(new BusinessException(WalletErrorCode.WALLET_INSUFFICIENT_BALANCE))
                .when(creditService)
                .spend(20L, 5, CreditTxType.POST_FEE, 1L);

        // when & then
        assertThatThrownBy(() -> requestService.selectReceiver(101L, 20L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
                });

        org.mockito.Mockito.verify(itemService, org.mockito.Mockito.never()).reserve(anyLong());
    }

    // Giao dịch Cho đã chọn người nhận (RESERVED): bài 1 của chủ bài 20, người nhận 10, chưa có món đem đổi.
    private Request givenReservedGiveRequest() {
        Request request = new Request(1L, 10L, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "id", 101L);
        request.select(Instant.now());
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "RESERVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        return request;
    }

    @Test
    @DisplayName("acceptOfferedItemInChat: Chủ bài đồng ý thì trừ POST_FEE, chuyển SWAP, gán và giữ chỗ món đề nghị")
    void acceptOfferedItemInChat_whenValid_chargesFeeConvertsAndReservesOfferedItem() {
        // given: món 50 của người nhận 10 đang APPROVED
        Request request = givenReservedGiveRequest();
        doReturn(Optional.of(new RequestService.ItemSnapshot(10L, "SWAP", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(50L);
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));

        // when
        RequestResponse response = requestService.acceptOfferedItemInChat(101L, 20L, 50L);

        // then
        verify(creditService).spend(20L, 5, CreditTxType.POST_FEE, 1L);
        verify(itemService).convertOfferType(1L, OfferType.SWAP);
        verify(itemService).reserve(50L);
        assertThat(request.getType()).isEqualTo("SWAP");
        assertThat(request.getOfferedItemId()).isEqualTo(50L);
        assertThat(response.offeredItemId()).isEqualTo(50L);
        verify(notificationService)
                .notify(eq(10L), eq("OFFER_CHANGE_ACCEPTED"), anyString(), anyString(), eq("/requests/101"));
    }

    @Test
    @DisplayName("acceptOfferedItemInChat: Người không phải chủ bài thì ném REQUEST_FORBIDDEN, không trừ tiền")
    void acceptOfferedItemInChat_whenNotDonor_throwsForbidden() {
        // given
        givenReservedGiveRequest();

        // when & then
        assertThatThrownBy(() -> requestService.acceptOfferedItemInChat(101L, 10L, 50L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_FORBIDDEN));
        verify(creditService, never()).spend(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any(), anyLong());
    }

    @Test
    @DisplayName("acceptOfferedItemInChat: Giao dịch đã là SWAP thì ném REQUEST_OFFER_CHANGE_NOT_ALLOWED")
    void acceptOfferedItemInChat_whenRequestIsSwap_throwsOfferChangeNotAllowed() {
        // given
        Request request = givenReservedGiveRequest();
        request.convertType("SWAP");

        // when & then
        assertThatThrownBy(() -> requestService.acceptOfferedItemInChat(101L, 20L, 50L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(RequestErrorCode.REQUEST_OFFER_CHANGE_NOT_ALLOWED));
        verify(itemService, never()).convertOfferType(anyLong(), any());
    }

    @Test
    @DisplayName("acceptOfferedItemInChat: Món đề nghị không phải của người nhận thì ném REQUEST_ITEM_NOT_AVAILABLE")
    void acceptOfferedItemInChat_whenOfferedItemNotOwnedByReceiver_throwsItemNotAvailable() {
        // given: món 50 thuộc người khác (30)
        givenReservedGiveRequest();
        doReturn(Optional.of(new RequestService.ItemSnapshot(30L, "SWAP", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(50L);

        // when & then
        assertThatThrownBy(() -> requestService.acceptOfferedItemInChat(101L, 20L, 50L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_ITEM_NOT_AVAILABLE));
        verify(creditService, never()).spend(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any(), anyLong());
    }

    @Test
    @DisplayName("acceptOfferedItemInChat: Chủ bài thiếu Credit thì ném lỗi ví và không đổi bài, không giữ chỗ món")
    void acceptOfferedItemInChat_whenInsufficientBalance_throwsAndChangesNothing() {
        // given
        Request request = givenReservedGiveRequest();
        doReturn(Optional.of(new RequestService.ItemSnapshot(10L, "SWAP", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(50L);
        org.mockito.Mockito.doThrow(new BusinessException(WalletErrorCode.WALLET_INSUFFICIENT_BALANCE))
                .when(creditService)
                .spend(20L, 5, CreditTxType.POST_FEE, 1L);

        // when & then
        assertThatThrownBy(() -> requestService.acceptOfferedItemInChat(101L, 20L, 50L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(WalletErrorCode.WALLET_INSUFFICIENT_BALANCE));
        verify(itemService, never()).convertOfferType(anyLong(), any());
        verify(itemService, never()).reserve(anyLong());
        assertThat(request.getType()).isEqualTo("GIVE");
        assertThat(request.getOfferedItemId()).isNull();
    }

    @Test
    @DisplayName(
            "Chủ bài Trao đổi chọn yêu cầu không kèm món: không hoàn phí, đổi sang GIVE và chỉ giữ chỗ 1 món (CD-04)")
    void selectReceiver_whenSwapToGive_convertsBothTypesAndReservesOnlyMainItem() {
        // given: bài SWAP, request không có offeredItemId
        Request selectedRequest = new Request(1L, 10L, null, "SWAP");
        org.springframework.test.util.ReflectionTestUtils.setField(selectedRequest, "id", 101L);
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "SWAP", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(requestRepository.findById(101L)).thenReturn(Optional.of(selectedRequest));
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));

        // when
        RequestResponse response = requestService.selectReceiver(101L, 20L);

        // then
        org.mockito.Mockito.verifyNoInteractions(creditService);
        org.mockito.Mockito.verify(itemService).convertOfferType(1L, OfferType.GIVE);
        assertThat(selectedRequest.getType()).isEqualTo("GIVE");
        assertThat(response.type()).isEqualTo("GIVE");
        org.mockito.Mockito.verify(itemService).reserve(1L);
        org.mockito.Mockito.verify(itemService, org.mockito.Mockito.never()).reserve(50L);
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
    @DisplayName("Ném lỗi khi chọn người nhận đang bị khoá tài khoản")
    void selectReceiver_whenReceiverLocked_throwsInvalidStateTransition() {
        Request request = new Request(1L, 10L, null, "GIVE");
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED", true)))
                .when(requestService)
                .findItemSnapshot(1L);
        when(userService.isLocked(10L)).thenReturn(true);

        assertThatThrownBy(() -> requestService.selectReceiver(101L, 20L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
                });
    }

    @Test
    @DisplayName("Ném lỗi khi bài đăng đã bị ẩn mà chủ bài cố chọn người nhận")
    void selectReceiver_whenItemNotActive_throwsItemNotAvailable() {
        Request request = new Request(1L, 10L, null, "GIVE");
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED", false)))
                .when(requestService)
                .findItemSnapshot(1L);

        assertThatThrownBy(() -> requestService.selectReceiver(101L, 20L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_ITEM_NOT_AVAILABLE);
                });
    }

    @Test
    @DisplayName("Ném lỗi khi chủ bài đang bị khoá mà cố chọn người nhận")
    void selectReceiver_whenDonorLocked_throwsForbidden() {
        Request request = new Request(1L, 10L, null, "GIVE");
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED", true)))
                .when(requestService)
                .findItemSnapshot(1L);
        when(userService.isLocked(20L)).thenReturn(true);

        assertThatThrownBy(() -> requestService.selectReceiver(101L, 20L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_FORBIDDEN);
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
    @DisplayName("getDetail: Bài đã RESERVED vẫn trả tiêu đề, ảnh và tên chủ bài cho người xin (U2)")
    void getDetail_whenItemReserved_returnsItemTitleImageAndDonorName() {
        // given: người xin (10) mở yêu cầu của bài đã được chọn người nhận, GET /items/{id} sẽ không cho họ xem bài này
        Request request = new Request(1L, 10L, 2L, "SWAP");
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "SWAP", "RESERVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(itemQueryService.getItemCard(1L))
                .thenReturn(new ItemCardDto(
                        1L, "Xe đạp mini", OfferType.SWAP, ItemStatus.RESERVED, "/api/v1/media/5", 20L));
        when(itemQueryService.findTitles(List.of(2L))).thenReturn(Map.of(2L, "Giày thể thao"));
        when(userService.findContact(10L)).thenReturn(new ContactInfo(10L, "Người xin", "b@example.com", "0901"));
        when(userService.findContact(20L)).thenReturn(new ContactInfo(20L, "Chủ bài", "a@example.com", "0902"));

        // when
        RequestResponse response = requestService.getDetail(101L, 10L);

        // then
        assertThat(response.itemTitle()).isEqualTo("Xe đạp mini");
        assertThat(response.itemImage()).isEqualTo("/api/v1/media/5");
        assertThat(response.donorId()).isEqualTo(20L);
        assertThat(response.donorName()).isEqualTo("Chủ bài");
        assertThat(response.offeredItemTitle()).isEqualTo("Giày thể thao");
        assertThat(response.receiverName()).isEqualTo("Người xin");
    }

    @Test
    @DisplayName("getDetail: Bài đang GIVE thì itemOfferType = GIVE và conversionFee = POST_FEE (CD-02)")
    void getDetail_whenItemIsGive_returnsPostFeeAsConversionFee() {
        // given: yêu cầu kèm món gửi tới bài GIVE (gửi chéo loại, CD-01), POST_FEE = 5 ở setUp
        Request request = new Request(1L, 10L, 2L, "GIVE");
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(itemQueryService.getItemCard(1L))
                .thenReturn(new ItemCardDto(1L, "Xe đạp mini", OfferType.GIVE, ItemStatus.APPROVED, null, 20L));

        // when
        RequestResponse response = requestService.getDetail(101L, 10L);

        // then
        assertThat(response.itemOfferType()).isEqualTo("GIVE");
        assertThat(response.conversionFee()).isEqualTo(5);
    }

    @Test
    @DisplayName("getDetail: Bài đang SWAP thì itemOfferType = SWAP và conversionFee = 0")
    void getDetail_whenItemIsSwap_returnsZeroConversionFee() {
        // given: yêu cầu không kèm món gửi tới bài SWAP (xin cho không, CD-01)
        Request request = new Request(1L, 10L, null, "SWAP");
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "SWAP", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(itemQueryService.getItemCard(1L))
                .thenReturn(new ItemCardDto(1L, "Xe đạp mini", OfferType.SWAP, ItemStatus.APPROVED, null, 20L));

        // when
        RequestResponse response = requestService.getDetail(101L, 10L);

        // then
        assertThat(response.itemOfferType()).isEqualTo("SWAP");
        assertThat(response.conversionFee()).isZero();
    }

    @Test
    @DisplayName("getDetail: Không tra được thẻ bài đăng thì các trường tên để null, API vẫn trả bình thường")
    void getDetail_whenItemCardMissing_leavesNamesNull() {
        // given
        Request request = new Request(1L, 10L, null, "GIVE");
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(itemQueryService.getItemCard(1L)).thenThrow(new BusinessException(ItemErrorCode.ITEM_NOT_FOUND));

        // when
        RequestResponse response = requestService.getDetail(101L, 10L);

        // then
        assertThat(response.itemTitle()).isNull();
        assertThat(response.donorName()).isNull();
        assertThat(response.offeredItemTitle()).isNull();
        assertThat(response.itemOfferType()).isNull();
        assertThat(response.conversionFee()).isNull();
        assertThat(response.itemId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("listMyRequests: Nhiều yêu cầu cùng một bài chỉ tra thẻ bài đăng một lần")
    void listMyRequests_whenSameItemTwice_looksUpItemCardOnce() {
        // given
        Request first = new Request(1L, 10L, null, "GIVE");
        Request second = new Request(1L, 10L, null, "GIVE");
        when(requestRepository.findByReceiverIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(first, second));
        when(itemQueryService.getItemCard(1L))
                .thenReturn(new ItemCardDto(1L, "Xe đạp mini", OfferType.GIVE, ItemStatus.APPROVED, null, 20L));
        when(userService.findContact(10L)).thenReturn(new ContactInfo(10L, "Người xin", "b@example.com", "0901"));
        when(userService.findContact(20L)).thenReturn(new ContactInfo(20L, "Chủ bài", "a@example.com", "0902"));

        // when
        List<RequestResponse> responses = requestService.listMyRequests(10L);

        // then
        assertThat(responses).extracting(RequestResponse::itemTitle).containsExactly("Xe đạp mini", "Xe đạp mini");
        verify(itemQueryService, times(1)).getItemCard(1L);
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
    @DisplayName("confirmLogistics: Sang LOGISTICS_CONFIRMED thì phát RequestNegotiationEndedEvent để chat huỷ đề nghị")
    void confirmLogistics_whenBothConfirm_publishesNegotiationEndedEvent() {
        // given: chủ bài đã đề xuất lịch, người nhận xác nhận nốt
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

        // when
        requestService.confirmLogistics(101L, 10L, new ConfirmLogisticsRequest(null));

        // then
        verify(eventPublisher).publishEvent(new RequestNegotiationEndedEvent(101L));
    }

    @Test
    @DisplayName("cancelTransaction: Huỷ giao dịch đã chọn người thì phát RequestNegotiationEndedEvent")
    void cancelTransaction_whenReserved_publishesNegotiationEndedEvent() {
        // given
        Request request = new Request(1L, 10L, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "id", 101L);
        request.select(Instant.now());
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "RESERVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        // when
        requestService.cancelTransaction(101L, 20L, new CancelTransactionRequest("Bận đột xuất"));

        // then
        verify(eventPublisher).publishEvent(new RequestNegotiationEndedEvent(101L));
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
    @DisplayName("U08: hoàn tất thì trả hạn khiếu nại theo cấu hình và báo cả hai bên kèm giờ hết hạn")
    void confirmHandover_whenCompleted_returnsComplaintDeadlineAndNotifiesItWithTime() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());
        request.proposeSchedule("IN_PERSON", "FPT", Instant.now().plus(1, ChronoUnit.DAYS), true, false, Instant.now());
        request.confirmLogistics(false, false, Instant.now());
        request.confirmHandover(true, null, Instant.now());
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(configService.getInt(ConfigKey.COMPLAINT_WINDOW_HOURS)).thenReturn(24);
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        RequestResponse response = requestService.confirmHandover(101L, 10L, new HandoverConfirmRequest(true, null));

        assertThat(response.complaintDeadline())
                .isEqualTo(request.getCompletedAt().plus(24, ChronoUnit.HOURS));
        String expectedBody = "Giao dịch đã được hai bên xác nhận hoàn tất thành công\\. Bạn có 24 giờ, đến"
                + " \\d{2}:\\d{2} \\d{2}/\\d{2}, để khiếu nại nếu có vấn đề\\.";
        for (long userId : List.of(10L, 20L)) {
            verify(notificationService)
                    .notify(
                            eq(userId),
                            eq("TRANSACTION_COMPLETED"),
                            any(),
                            org.mockito.ArgumentMatchers.matches(expectedBody),
                            any());
        }
    }

    @Test
    @DisplayName(
            "confirmHandover: Người nhận chọn không đúng mô tả -> chuyển DISPUTED, không markTraded, phát report event")
    void confirmHandover_whenReceiverSaysNotAsDescribed_publishesReportEvent() {
        // given
        stubLogisticsConfirmedRequest();

        // when
        RequestResponse response =
                requestService.confirmHandover(101L, 10L, new HandoverConfirmRequest(false, "  Máy không bật được  "));

        // then
        assertThat(response.status()).isEqualTo("DISPUTED");
        verify(itemService, never()).markTraded(anyLong());
        verify(eventPublisher).publishEvent(new NotAsDescribedReportedEvent(101L, 10L, "Máy không bật được"));
    }

    @Test
    @DisplayName(
            "confirmHandover: Người nhận xác nhận sau donor nhưng chọn không đúng mô tả -> chuyển DISPUTED, không COMPLETED")
    void confirmHandover_whenReceiverConfirmsAfterDonorNotAsDescribed_transitionsToDisputed() {
        // given: donor đã xác nhận trước -> status là AWAITING_HANDOVER
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());
        request.proposeSchedule(
                "IN_PERSON",
                "FPT",
                Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS),
                true,
                false,
                Instant.now());
        request.confirmLogistics(false, false, Instant.now());
        request.confirmHandover(true, null, Instant.now());

        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);

        // when
        RequestResponse response =
                requestService.confirmHandover(101L, 10L, new HandoverConfirmRequest(false, "Món đồ bị vỡ màn hình"));

        // then
        assertThat(response.status()).isEqualTo("DISPUTED");
        verify(itemService, never()).markTraded(anyLong());
        verify(eventPublisher).publishEvent(new NotAsDescribedReportedEvent(101L, 10L, "Món đồ bị vỡ màn hình"));
    }

    @Test
    void confirmHandover_whenReceiverSaysAsDescribed_publishesNoReportEvent() {
        // given
        stubLogisticsConfirmedRequest();

        // when
        requestService.confirmHandover(101L, 10L, new HandoverConfirmRequest(true, null));

        // then
        verify(eventPublisher, never()).publishEvent(any(NotAsDescribedReportedEvent.class));
    }

    @Test
    void confirmHandover_whenDonorConfirms_publishesNoReportEvent() {
        // given
        stubLogisticsConfirmedRequest();

        // when
        requestService.confirmHandover(101L, 20L, new HandoverConfirmRequest(null, null));

        // then
        verify(eventPublisher, never()).publishEvent(any(NotAsDescribedReportedEvent.class));
    }

    /** Request đã chốt lịch (LOGISTICS_CONFIRMED) của bên nhận 10, bài đăng thuộc bên cho 20. */
    private void stubLogisticsConfirmedRequest() {
        Request request = new Request(1L, 10L, null, "GIVE");
        request.select(Instant.now());
        request.proposeSchedule("IN_PERSON", "FPT", Instant.now().plus(1, ChronoUnit.DAYS), true, false, Instant.now());
        request.confirmLogistics(false, false, Instant.now());
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
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

    @Test
    @DisplayName(
            "listByItem: Trả kèm TrustStars, hạng và tỉ lệ đúng mô tả lấy từ ReputationService (YC-06, Issue #110)")
    void listByItem_whenDonorCalls_returnsReceiverReputationInfo() {
        // given
        Request request = new Request(1L, 10L, null, "GIVE");
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(requestRepository.findByItemIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(request));
        when(userService.findContact(10L))
                .thenReturn(new ContactInfo(10L, "Nguyễn Văn B", "receiver@example.com", "0901234567"));
        when(reputationService.getReputationSummary(10L)).thenReturn(new ReputationSummary(10L, 5, Rank.SILVER, 95.5));

        // when
        List<RequestResponse> responses = requestService.listByItem(1L, 20L); // donor gọi

        // then
        assertThat(responses).hasSize(1);
        RequestResponse resp = responses.get(0);
        assertThat(resp.receiverName()).isEqualTo("Nguyễn Văn B");
        assertThat(resp.receiverTrustStars()).isEqualTo(5);
        assertThat(resp.trustStars()).isEqualTo(5);
        assertThat(resp.receiverRank()).isEqualTo("SILVER");
        assertThat(resp.rank()).isEqualTo("SILVER");
        assertThat(resp.receiverAsDescribedRate()).isEqualTo(95.5);
        assertThat(resp.asDescribedRate()).isEqualTo(95.5);
    }

    @Test
    @DisplayName("listByItem: Khi ReputationService ném lỗi, vẫn trả về danh sách an toàn với giá trị mặc định")
    void listByItem_whenReputationServiceFails_returnsDefaultReputationGracefully() {
        // given
        Request request = new Request(1L, 10L, null, "GIVE");
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(requestRepository.findByItemIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(request));
        when(userService.findContact(10L))
                .thenReturn(new ContactInfo(10L, "Nguyễn Văn B", "receiver@example.com", "0901234567"));
        when(reputationService.getReputationSummary(10L))
                .thenThrow(new RuntimeException("Database error in reputation"));

        // when
        List<RequestResponse> responses = requestService.listByItem(1L, 20L);

        // then
        assertThat(responses).hasSize(1);
        RequestResponse resp = responses.get(0);
        assertThat(resp.receiverName()).isEqualTo("Nguyễn Văn B");
        assertThat(resp.receiverTrustStars()).isEqualTo(5);
        assertThat(resp.receiverRank()).isEqualTo("BRONZE");
        assertThat(resp.receiverAsDescribedRate()).isNull();
    }

    @Test
    @DisplayName("getDetail: Khi chủ bài xem chi tiết yêu cầu, trả kèm thông tin uy tín của người xin")
    void getDetail_whenDonorCalls_returnsReceiverReputationInfo() {
        // given
        Request request = new Request(1L, 10L, null, "GIVE");
        when(requestRepository.findById(101L)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(20L, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(1L);
        when(userService.findContact(10L))
                .thenReturn(new ContactInfo(10L, "Nguyễn Văn B", "receiver@example.com", "0901234567"));
        when(reputationService.getReputationSummary(10L)).thenReturn(new ReputationSummary(10L, 4, Rank.GOLD, 100.0));

        // when
        RequestResponse resp = requestService.getDetail(101L, 20L); // donor xem

        // then
        assertThat(resp.receiverName()).isEqualTo("Nguyễn Văn B");
        assertThat(resp.receiverTrustStars()).isEqualTo(4);
        assertThat(resp.receiverRank()).isEqualTo("GOLD");
        assertThat(resp.receiverAsDescribedRate()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("cancelPendingRequestsForItemRemoved: Huỷ các yêu cầu PENDING và gửi thông báo khi bài bị gỡ")
    void cancelPendingRequestsForItemRemoved_cancelsPendingRequestsAndNotifies() {
        // given
        Request req1 = new Request(1L, 10L, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(req1, "id", 101L);
        Request req2 = new Request(1L, 11L, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(req2, "id", 102L);

        when(requestRepository.findByItemIdAndStatus(1L, RequestStatus.PENDING)).thenReturn(List.of(req1, req2));

        // when
        int count = requestService.cancelPendingRequestsForItemRemoved(1L);

        // then
        assertThat(count).isEqualTo(2);
        assertThat(req1.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(req1.getCancelReason()).isEqualTo("[Hệ thống] Bài đã bị gỡ");
        assertThat(req2.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(req2.getCancelReason()).isEqualTo("[Hệ thống] Bài đã bị gỡ");

        verify(notificationService)
                .notify(eq(10L), eq("REQUEST_CANCELLED"), anyString(), anyString(), eq("/requests/101"));
        verify(notificationService)
                .notify(eq(11L), eq("REQUEST_CANCELLED"), anyString(), anyString(), eq("/requests/102"));
    }

    @Test
    @DisplayName("U5-BE: getDetail trả disputeResponseDueAt khi request ở trạng thái DISPUTED")
    void getDetail_whenDisputed_returnsDisputeResponseDueAt() {
        long requestId = 101L;
        long donorId = 1L;
        long receiverId = 2L;
        long itemId = 10L;
        Instant dueAt = Instant.parse("2026-11-03T10:00:00Z");

        Request request = new Request(itemId, receiverId, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "id", requestId);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "status", RequestStatus.DISPUTED);

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(donorId, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(itemId);
        when(configService.getInt(ConfigKey.COMPLAINT_WINDOW_HOURS)).thenReturn(24);
        when(reportService.findDisputeResponseDueAt(requestId)).thenReturn(dueAt);

        RequestResponse response = requestService.getDetail(requestId, receiverId);

        assertThat(response.status()).isEqualTo(RequestStatus.DISPUTED.name());
        assertThat(response.disputeResponseDueAt()).isEqualTo(dueAt);
        verify(reportService).findDisputeResponseDueAt(requestId);
    }

    @Test
    @DisplayName("U5-BE: getDetail trả disputeResponseDueAt null khi request không phải DISPUTED")
    void getDetail_whenNotDisputed_disputeResponseDueAtIsNull() {
        long requestId = 102L;
        long donorId = 1L;
        long receiverId = 2L;
        long itemId = 10L;

        Request request = new Request(itemId, receiverId, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "id", requestId);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "status", RequestStatus.RESERVED);

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        doReturn(Optional.of(new RequestService.ItemSnapshot(donorId, "GIVE", "APPROVED")))
                .when(requestService)
                .findItemSnapshot(itemId);
        when(configService.getInt(ConfigKey.COMPLAINT_WINDOW_HOURS)).thenReturn(24);

        RequestResponse response = requestService.getDetail(requestId, receiverId);

        assertThat(response.status()).isEqualTo(RequestStatus.RESERVED.name());
        assertThat(response.disputeResponseDueAt()).isNull();
        verify(reportService, never()).findDisputeResponseDueAt(anyLong());
    }

    @Test
    @DisplayName("U5-BE: listMyRequests trả disputeResponseDueAt cho các request DISPUTED")
    void listMyRequests_whenHasDisputed_includesDisputeResponseDueAt() {
        long currentUserId = 2L;
        long reqId1 = 201L;
        long reqId2 = 202L;
        Instant dueAt = Instant.parse("2026-11-03T18:00:00Z");

        Request reqDisputed = new Request(10L, currentUserId, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(reqDisputed, "id", reqId1);
        org.springframework.test.util.ReflectionTestUtils.setField(reqDisputed, "status", RequestStatus.DISPUTED);

        Request reqPending = new Request(11L, currentUserId, null, "GIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(reqPending, "id", reqId2);
        org.springframework.test.util.ReflectionTestUtils.setField(reqPending, "status", RequestStatus.PENDING);

        when(requestRepository.findByReceiverIdOrderByCreatedAtDesc(currentUserId))
                .thenReturn(List.of(reqDisputed, reqPending));
        when(configService.getInt(ConfigKey.COMPLAINT_WINDOW_HOURS)).thenReturn(24);
        when(reportService.findDisputeResponseDueAtMap(List.of(reqId1))).thenReturn(Map.of(reqId1, dueAt));

        List<RequestResponse> list = requestService.listMyRequests(currentUserId);

        assertThat(list).hasSize(2);
        RequestResponse respDisputed =
                list.stream().filter(r -> r.id().equals(reqId1)).findFirst().orElseThrow();
        RequestResponse respPending =
                list.stream().filter(r -> r.id().equals(reqId2)).findFirst().orElseThrow();

        assertThat(respDisputed.disputeResponseDueAt()).isEqualTo(dueAt);
        assertThat(respPending.disputeResponseDueAt()).isNull();
    }
}
