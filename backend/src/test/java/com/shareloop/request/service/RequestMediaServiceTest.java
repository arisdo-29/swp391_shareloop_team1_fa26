package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemService;
import com.shareloop.media.MediaErrorCode;
import com.shareloop.media.service.MediaService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.HandoverConfirmRequest;
import com.shareloop.request.dto.RequestMediaResponse;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestMediaMapping;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.repository.RequestMediaRepository;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.service.UserService;
import java.util.Collections;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit test kiểm thử tính năng đính kèm và xem ảnh/video minh chứng giao nhận (FR-86, BR-T09, Issue #145).
 */
@ExtendWith(MockitoExtension.class)
class RequestMediaServiceTest {

    @Mock
    RequestRepository requestRepository;

    @Mock
    NotificationService notificationService;

    @Mock
    ConfigService configService;

    @Mock
    ItemService itemService;

    @Mock
    UserService userService;

    @Mock
    ReputationService reputationService;

    @Mock
    MediaService mediaService;

    @Mock
    RequestMediaRepository requestMediaRepository;

    @Spy
    @InjectMocks
    RequestService requestService;

    private static final long REQUEST_ID = 100L;
    private static final long ITEM_ID = 1L;
    private static final long DONOR_ID = 10L;
    private static final long RECEIVER_ID = 20L;
    private static final long STRANGER_ID = 99L;

    private Request request;

    @BeforeEach
    void setUp() {
        request = new Request(ITEM_ID, RECEIVER_ID, null, "GIVE");
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", RequestStatus.LOGISTICS_CONFIRMED);

        // Giả lập item snapshot
        org.mockito.Mockito.lenient()
                .doReturn(Optional.of(new RequestService.ItemSnapshot(DONOR_ID, "GIVE", "RESERVED")))
                .when(requestService)
                .findItemSnapshot(ITEM_ID);
    }

    @Test
    @DisplayName("Thành công: Đính kèm minh chứng khi là bên nhận (receiver) ở trạng thái LOGISTICS_CONFIRMED")
    void attachMedia_whenReceiver_success() {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        List<Long> mediaIds = List.of(501L, 502L);

        RequestMediaMapping m1 = new RequestMediaMapping(REQUEST_ID, 501L, "HANDOVER", RECEIVER_ID);
        RequestMediaMapping m2 = new RequestMediaMapping(REQUEST_ID, 502L, "HANDOVER", RECEIVER_ID);
        when(requestMediaRepository.findByRequestIdOrderByCreatedAtAsc(REQUEST_ID))
                .thenReturn(List.of(m1, m2));

        List<RequestMediaResponse> result = requestService.attachMedia(REQUEST_ID, RECEIVER_ID, mediaIds, "HANDOVER");

        // Xác nhận đã gọi MediaService.attach để chuyển trạng thái media sang ATTACHED
        verify(mediaService).attach(mediaIds, RECEIVER_ID);
        verify(requestMediaRepository, org.mockito.Mockito.times(2)).save(any(RequestMediaMapping.class));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).mediaId()).isEqualTo(501L);
        assertThat(result.get(0).url()).isEqualTo("/api/v1/media/501");
        assertThat(result.get(0).stage()).isEqualTo("HANDOVER");
        assertThat(result.get(0).uploadedBy()).isEqualTo(RECEIVER_ID);
    }

    @Test
    @DisplayName("Thành công: Đính kèm minh chứng khi là chủ bài (donor) ở trạng thái AWAITING_HANDOVER")
    void attachMedia_whenDonor_success() {
        ReflectionTestUtils.setField(request, "status", RequestStatus.AWAITING_HANDOVER);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        List<Long> mediaIds = List.of(601L);

        RequestMediaMapping m = new RequestMediaMapping(REQUEST_ID, 601L, "HANDOVER", DONOR_ID);
        when(requestMediaRepository.findByRequestIdOrderByCreatedAtAsc(REQUEST_ID))
                .thenReturn(List.of(m));

        List<RequestMediaResponse> result = requestService.attachMedia(REQUEST_ID, DONOR_ID, mediaIds, null);

        verify(mediaService).attach(mediaIds, DONOR_ID);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).mediaId()).isEqualTo(601L);
        assertThat(result.get(0).stage()).isEqualTo("HANDOVER"); // Mặc định là HANDOVER
    }

    @Test
    @DisplayName("Thành công: Đính kèm minh chứng trực tiếp trong confirmHandover")
    void confirmHandover_withMediaIds_attachesMedia() {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> i.getArgument(0));

        List<Long> mediaIds = List.of(701L);
        HandoverConfirmRequest dto = new HandoverConfirmRequest(true, null, mediaIds);

        requestService.confirmHandover(REQUEST_ID, RECEIVER_ID, dto);

        verify(mediaService).attach(mediaIds, RECEIVER_ID);
        verify(requestMediaRepository).save(any(RequestMediaMapping.class));
    }

    @Test
    @DisplayName("Ném REQUEST_NOT_FOUND (404) khi không tìm thấy yêu cầu")
    void attachMedia_whenRequestNotFound_throwsException() {
        when(requestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestService.attachMedia(999L, RECEIVER_ID, List.of(1L), "HANDOVER"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_NOT_FOUND);
                });

        verify(mediaService, never()).attach(any(), anyLong());
    }

    @Test
    @DisplayName("Ném REQUEST_FORBIDDEN (403) khi người gọi không phải bên cho và không phải bên nhận")
    void attachMedia_whenStranger_throwsForbidden() {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> requestService.attachMedia(REQUEST_ID, STRANGER_ID, List.of(1L), "HANDOVER"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_FORBIDDEN);
                });

        verify(mediaService, never()).attach(any(), anyLong());
    }

    @Test
    @DisplayName(
            "Ném REQUEST_INVALID_STATE_TRANSITION (409) khi request chưa đến giai đoạn giao nhận (PENDING, RESERVED...)")
    void attachMedia_whenInvalidStatus_throwsConflict() {
        ReflectionTestUtils.setField(request, "status", RequestStatus.PENDING);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> requestService.attachMedia(REQUEST_ID, RECEIVER_ID, List.of(1L), "HANDOVER"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
                });

        verify(mediaService, never()).attach(any(), anyLong());
    }

    @Test
    @DisplayName("Ném REQUEST_MEDIA_REQUIRED (400) khi danh sách mediaIds rỗng")
    void attachMedia_whenEmptyMediaIds_throwsBadRequest() {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(
                        () -> requestService.attachMedia(REQUEST_ID, RECEIVER_ID, Collections.emptyList(), "HANDOVER"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_MEDIA_REQUIRED);
                });

        verify(mediaService, never()).attach(any(), anyLong());
    }

    @Test
    @DisplayName("Ném REQUEST_MEDIA_STAGE_INVALID (400) khi stage không hợp lệ")
    void attachMedia_whenInvalidStage_throwsBadRequest() {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> requestService.attachMedia(REQUEST_ID, RECEIVER_ID, List.of(1L), "UNKNOWN_STAGE"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_MEDIA_STAGE_INVALID);
                });

        verify(mediaService, never()).attach(any(), anyLong());
    }

    @Test
    @DisplayName("Lan truyền lỗi từ MediaService nếu file không thuộc quyền sở hữu của người gọi")
    void attachMedia_whenNotMediaOwner_propagatesException() {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        org.mockito.Mockito.doThrow(new BusinessException(MediaErrorCode.MEDIA_NOT_OWNER))
                .when(mediaService)
                .attach(any(), anyLong());

        assertThatThrownBy(() -> requestService.attachMedia(REQUEST_ID, RECEIVER_ID, List.of(999L), "HANDOVER"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(MediaErrorCode.MEDIA_NOT_OWNER);
                });

        verify(requestMediaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chỉ hai bên xem được: getMediaList thành công với chủ bài đăng và người nhận")
    void getMediaList_twoPartiesAllowed() {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        RequestMediaMapping m = new RequestMediaMapping(REQUEST_ID, 1L, "HANDOVER", DONOR_ID);
        when(requestMediaRepository.findByRequestIdOrderByCreatedAtAsc(REQUEST_ID))
                .thenReturn(List.of(m));

        // Chủ bài xem
        List<RequestMediaResponse> donorResult = requestService.getMediaList(REQUEST_ID, DONOR_ID);
        assertThat(donorResult).hasSize(1);

        // Người nhận xem
        List<RequestMediaResponse> receiverResult = requestService.getMediaList(REQUEST_ID, RECEIVER_ID);
        assertThat(receiverResult).hasSize(1);
    }

    @Test
    @DisplayName("Chỉ hai bên xem được (BR-T09): Ném REQUEST_FORBIDDEN (403) khi người lạ xem minh chứng")
    void getMediaList_strangerBlocked_throwsForbidden() {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> requestService.getMediaList(REQUEST_ID, STRANGER_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_FORBIDDEN);
                });
    }
}
