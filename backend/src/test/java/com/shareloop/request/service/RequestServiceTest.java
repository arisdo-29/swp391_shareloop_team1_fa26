package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.CreateRequest;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
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

        RequestResponse response = requestService.cancelRequest(55L, 10L);

        assertThat(response.status()).isEqualTo("CANCELLED");
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
}
