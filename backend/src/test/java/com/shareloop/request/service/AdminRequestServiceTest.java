package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.request.dto.AdminRequestPageResponse;
import com.shareloop.request.dto.CancelTransactionRequest;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");
    private static final long DONOR_ID = 20L;
    private static final long RECEIVER_ID = 10L;

    @Mock
    RequestRepository requestRepository;

    @Mock
    RequestService requestService;

    @Mock
    ItemQueryService itemQueryService;

    @Mock
    UserService userService;

    @Mock
    ConfigService configService;

    AdminRequestService adminRequestService;

    @BeforeEach
    void setUp() {
        adminRequestService = new AdminRequestService(
                requestRepository,
                requestService,
                itemQueryService,
                userService,
                configService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        org.mockito.Mockito.lenient()
                .when(configService.getInt(ConfigKey.STUCK_REQUEST_HOURS))
                .thenReturn(48);
        org.mockito.Mockito.lenient()
                .when(itemQueryService.getSummary(1L))
                .thenReturn(new ItemSummary(1L, DONOR_ID, null, null));
        org.mockito.Mockito.lenient()
                .when(itemQueryService.findTitles(Set.of(1L)))
                .thenReturn(Map.of(1L, "Xe đạp cũ"));
        org.mockito.Mockito.lenient()
                .when(userService.findBriefs(Set.of(DONOR_ID, RECEIVER_ID)))
                .thenReturn(Map.of(
                        DONOR_ID, new UserBrief(DONOR_ID, "An Chủ Bài", "an@test.local"),
                        RECEIVER_ID, new UserBrief(RECEIVER_ID, "Bình Người Nhận", "binh@test.local")));
    }

    private Request request(long id, RequestStatus status, Instant meetingTime) {
        Request request = new Request(1L, RECEIVER_ID, null, "GIVE");
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "status", status);
        ReflectionTestUtils.setField(request, "meetingTime", meetingTime);
        ReflectionTestUtils.setField(request, "createdAt", NOW.minus(10, ChronoUnit.DAYS));
        return request;
    }

    @Test
    @DisplayName("stuck=true: lấy giao dịch quá giờ hẹn hơn 48 giờ mà chưa bên nào xác nhận, kèm lý do gắn cờ")
    void list_whenStuckTrue_returnsOnlyOverdueUnconfirmedRequests() {
        // given
        Request overdue = request(1L, RequestStatus.LOGISTICS_CONFIRMED, NOW.minus(49, ChronoUnit.HOURS));
        Request notYetOverdue = request(2L, RequestStatus.LOGISTICS_CONFIRMED, NOW.minus(47, ChronoUnit.HOURS));
        Request oneSideConfirmed = request(3L, RequestStatus.AWAITING_HANDOVER, NOW.minus(60, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(oneSideConfirmed, "donorHandoverAt", NOW.minus(50, ChronoUnit.HOURS));
        when(requestRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(overdue, notYetOverdue, oneSideConfirmed));

        // when
        AdminRequestPageResponse page = adminRequestService.list(null, true, null, 0, 20);

        // then
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo(1L);
        assertThat(page.items().get(0).flagReason()).contains("48 giờ");
        assertThat(page.items().get(0).donorName()).isEqualTo("An Chủ Bài");
        assertThat(page.items().get(0).itemTitle()).isEqualTo("Xe đạp cũ");
    }

    @Test
    @DisplayName("stuck=true: giao dịch có một bên đang bị khoá cũng bị gắn cờ")
    void list_whenOnePartyLocked_flagsRequest() {
        // given
        Request reserved = request(4L, RequestStatus.RESERVED, null);
        when(requestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(reserved));
        when(userService.findLockedIds(Set.of(DONOR_ID, RECEIVER_ID))).thenReturn(Set.of(RECEIVER_ID));

        // when
        AdminRequestPageResponse page = adminRequestService.list(null, true, null, 0, 20);

        // then
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).flagReason()).isEqualTo("Tài khoản người nhận đang bị khoá");
    }

    @Test
    @DisplayName("stuck=false: bỏ giao dịch bị kẹt; giao dịch đã kết thúc không bao giờ bị gắn cờ")
    void list_whenStuckFalse_excludesStuckRequests() {
        // given
        Request overdue = request(1L, RequestStatus.LOGISTICS_CONFIRMED, NOW.minus(49, ChronoUnit.HOURS));
        Request completed = request(5L, RequestStatus.COMPLETED, NOW.minus(100, ChronoUnit.HOURS));
        when(requestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(overdue, completed));

        // when
        AdminRequestPageResponse page = adminRequestService.list(null, false, null, 0, 20);

        // then
        assertThat(page.items()).extracting("id").containsExactly(5L);
    }

    @Test
    @DisplayName("Lọc theo trạng thái dùng truy vấn theo status; q khớp tên bài hoặc tên hai bên")
    void list_whenStatusAndQuery_filtersByBoth() {
        // given
        Request awaiting = request(6L, RequestStatus.AWAITING_HANDOVER, null);
        when(requestRepository.findByStatusOrderByCreatedAtDesc(RequestStatus.AWAITING_HANDOVER))
                .thenReturn(List.of(awaiting));

        // when
        AdminRequestPageResponse matched =
                adminRequestService.list(RequestStatus.AWAITING_HANDOVER, null, "xe ĐẠP", 0, 20);
        AdminRequestPageResponse missed =
                adminRequestService.list(RequestStatus.AWAITING_HANDOVER, null, "không có", 0, 20);

        // then
        assertThat(matched.items()).hasSize(1);
        assertThat(missed.items()).isEmpty();
    }

    @Test
    @DisplayName("Phân trang: trả đúng trang, tổng số dòng và tổng số trang")
    void list_paginatesResults() {
        // given
        Request first = request(7L, RequestStatus.RESERVED, null);
        Request second = request(8L, RequestStatus.RESERVED, null);
        Request third = request(9L, RequestStatus.RESERVED, null);
        when(requestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(first, second, third));

        // when
        AdminRequestPageResponse page = adminRequestService.list(null, null, null, 1, 2);

        // then
        assertThat(page.items()).extracting("id").containsExactly(9L);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Huỷ giao dịch chuyển sang RequestService.cancelByAdmin")
    void cancel_delegatesToRequestService() {
        // given
        CancelTransactionRequest dto = new CancelTransactionRequest("Hai bên không liên lạc được");

        // when
        adminRequestService.cancel(1L, 6L, dto);

        // then
        verify(requestService).cancelByAdmin(6L, 1L, dto);
    }
}
