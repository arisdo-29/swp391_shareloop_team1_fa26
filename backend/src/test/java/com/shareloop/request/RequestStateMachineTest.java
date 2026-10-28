package com.shareloop.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit test kiểm thử toàn diện máy trạng thái Request entity (ERD 5.1, Hình 21; Spike S8; Issue #127).
 * Kiểm tra:
 * 1. Từng mũi tên hợp lệ (14 test độc lập với các domain methods tương ứng).
 * 2. Mọi chuyển trạng thái không hợp lệ trong ma trận đều ném BusinessException(REQUEST_INVALID_STATE_TRANSITION).
 * 3. Các quy tắc nghiệp vụ ràng buộc trạng thái: BR-T05 (khoá lịch sau khi đã chốt), BR-R01, waiver khi shipping, v.v.
 * 4. Các kịch bản vòng đời hoàn chỉnh (End-to-End Lifecycle Flows).
 */
class RequestStateMachineTest {

    private static final Set<String> VALID_TRANSITION_KEYS = Set.of(
            "PENDING->RESERVED",
            "PENDING->REJECTED",
            "PENDING->CANCELLED",
            "RESERVED->AWAITING_LOGISTICS",
            "RESERVED->CANCELLED",
            "AWAITING_LOGISTICS->LOGISTICS_CONFIRMED",
            "AWAITING_LOGISTICS->CANCELLED",
            "LOGISTICS_CONFIRMED->AWAITING_HANDOVER",
            "LOGISTICS_CONFIRMED->CANCELLED",
            "AWAITING_HANDOVER->COMPLETED",
            "AWAITING_HANDOVER->DISPUTED",
            "AWAITING_HANDOVER->CANCELLED",
            "DISPUTED->COMPLETED",
            "DISPUTED->CANCELLED");

    private static String key(RequestStatus from, RequestStatus to) {
        return from.name() + "->" + to.name();
    }

    private Request createRequest(RequestStatus initialStatus) {
        Request request = new Request(1L, 10L, null, "GIVE");
        ReflectionTestUtils.setField(request, "id", 100L);
        ReflectionTestUtils.setField(request, "status", initialStatus);
        return request;
    }

    static Stream<Arguments> provideInvalidTransitions() {
        List<Arguments> invalidList = new ArrayList<>();
        for (RequestStatus from : RequestStatus.values()) {
            for (RequestStatus to : RequestStatus.values()) {
                if (!VALID_TRANSITION_KEYS.contains(key(from, to))) {
                    invalidList.add(Arguments.of(from, to));
                }
            }
        }
        return invalidList.stream();
    }

    @Nested
    @DisplayName("1. Kiểm thử từng mũi tên hợp lệ trên Request entity")
    class ValidEntityTransitions {

        @Test
        @DisplayName("Mũi tên 1: PENDING -> RESERVED (Chủ bài chọn người nhận qua select())")
        void pendingToReserved() {
            Request request = createRequest(RequestStatus.PENDING);
            Instant now = Instant.now();

            request.select(now);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.RESERVED);
            assertThat(request.getReservedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("Mũi tên 1b: PENDING -> RESERVED (chuyển trực tiếp qua transitionTo())")
        void pendingToReserved_directTransition() {
            Request request = createRequest(RequestStatus.PENDING);
            request.transitionTo(RequestStatus.RESERVED);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.RESERVED);
        }

        @Test
        @DisplayName("Mũi tên 2: PENDING -> REJECTED (Chủ bài từ chối qua reject())")
        void pendingToRejected() {
            Request request = createRequest(RequestStatus.PENDING);

            request.reject();

            assertThat(request.getStatus()).isEqualTo(RequestStatus.REJECTED);
        }

        @Test
        @DisplayName("Mũi tên 2b: PENDING -> REJECTED (chuyển trực tiếp qua transitionTo())")
        void pendingToRejected_directTransition() {
            Request request = createRequest(RequestStatus.PENDING);
            request.transitionTo(RequestStatus.REJECTED);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.REJECTED);
        }

        @Test
        @DisplayName("Mũi tên 3: PENDING -> CANCELLED (Người gửi tự huỷ qua cancel())")
        void pendingToCancelled() {
            Request request = createRequest(RequestStatus.PENDING);

            request.cancel("Người gửi đổi ý");

            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
            assertThat(request.getCancelReason()).isEqualTo("Người gửi đổi ý");
        }

        @Test
        @DisplayName("Mũi tên 3b: PENDING -> CANCELLED (chuyển trực tiếp qua transitionTo())")
        void pendingToCancelled_directTransition() {
            Request request = createRequest(RequestStatus.PENDING);
            request.transitionTo(RequestStatus.CANCELLED);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        }

        @Test
        @DisplayName("Mũi tên 4: RESERVED -> AWAITING_LOGISTICS (Đề xuất lịch hẹn qua proposeSchedule())")
        void reservedToAwaitingLogistics() {
            Request request = createRequest(RequestStatus.RESERVED);
            Instant meetingTime = Instant.now().plus(2, ChronoUnit.DAYS);
            Instant now = Instant.now();

            request.proposeSchedule("IN_PERSON", "Cổng trường ĐH FPT", meetingTime, true, false, now);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_LOGISTICS);
            assertThat(request.getDeliveryMethod()).isEqualTo("IN_PERSON");
            assertThat(request.getMeetingPlace()).isEqualTo("Cổng trường ĐH FPT");
            assertThat(request.getMeetingTime()).isEqualTo(meetingTime);
            assertThat(request.getDonorScheduleOkAt()).isEqualTo(now);
            assertThat(request.getReceiverScheduleOkAt()).isNull();
        }

        @Test
        @DisplayName("Mũi tên 4b: RESERVED -> AWAITING_LOGISTICS (chuyển trực tiếp qua transitionTo())")
        void reservedToAwaitingLogistics_directTransition() {
            Request request = createRequest(RequestStatus.RESERVED);
            request.transitionTo(RequestStatus.AWAITING_LOGISTICS);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_LOGISTICS);
        }

        @Test
        @DisplayName("Mũi tên 5: RESERVED -> CANCELLED (Huỷ do quá hạn 10 ngày không chốt lịch qua cancel())")
        void reservedToCancelled() {
            Request request = createRequest(RequestStatus.RESERVED);

            request.cancel("Quá hạn 10 ngày không chốt lịch");

            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
            assertThat(request.getCancelReason()).isEqualTo("Quá hạn 10 ngày không chốt lịch");
        }

        @Test
        @DisplayName("Mũi tên 5b: RESERVED -> CANCELLED (chuyển trực tiếp qua transitionTo())")
        void reservedToCancelled_directTransition() {
            Request request = createRequest(RequestStatus.RESERVED);
            request.transitionTo(RequestStatus.CANCELLED);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        }

        @Test
        @DisplayName(
                "Mũi tên 6: AWAITING_LOGISTICS -> LOGISTICS_CONFIRMED (Cả hai bên xác nhận lịch qua confirmLogistics())")
        void awaitingLogisticsToLogisticsConfirmed() {
            Request request = createRequest(RequestStatus.AWAITING_LOGISTICS);
            Instant meetingTime = Instant.now().plus(2, ChronoUnit.DAYS);
            Instant t1 = Instant.now();
            Instant t2 = t1.plus(5, ChronoUnit.MINUTES);

            // Bên cho đề xuất lịch
            request.proposeSchedule("IN_PERSON", "Cổng trường", meetingTime, true, false, t1);

            // Bên nhận xác nhận đồng ý lịch
            request.confirmLogistics(false, false, t2);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
            assertThat(request.getContactRevealedAt()).isEqualTo(t2);
        }

        @Test
        @DisplayName("Mũi tên 6b: AWAITING_LOGISTICS -> LOGISTICS_CONFIRMED (chuyển trực tiếp qua transitionTo())")
        void awaitingLogisticsToLogisticsConfirmed_directTransition() {
            Request request = createRequest(RequestStatus.AWAITING_LOGISTICS);
            request.transitionTo(RequestStatus.LOGISTICS_CONFIRMED);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
        }

        @Test
        @DisplayName("Mũi tên 7: AWAITING_LOGISTICS -> CANCELLED (Huỷ trước khi chốt lịch qua cancel())")
        void awaitingLogisticsToCancelled() {
            Request request = createRequest(RequestStatus.AWAITING_LOGISTICS);

            request.cancel("Không sắp xếp được thời gian");

            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
            assertThat(request.getCancelReason()).isEqualTo("Không sắp xếp được thời gian");
        }

        @Test
        @DisplayName("Mũi tên 7b: AWAITING_LOGISTICS -> CANCELLED (chuyển trực tiếp qua transitionTo())")
        void awaitingLogisticsToCancelled_directTransition() {
            Request request = createRequest(RequestStatus.AWAITING_LOGISTICS);
            request.transitionTo(RequestStatus.CANCELLED);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        }

        @Test
        @DisplayName(
                "Mũi tên 8: LOGISTICS_CONFIRMED -> AWAITING_HANDOVER (Một bên bấm xác nhận giao nhận qua confirmHandover())")
        void logisticsConfirmedToAwaitingHandover() {
            Request request = createRequest(RequestStatus.LOGISTICS_CONFIRMED);
            Instant now = Instant.now();

            // Chủ bài bấm xác nhận đã giao
            request.confirmHandover(true, null, now);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_HANDOVER);
            assertThat(request.getDonorHandoverAt()).isEqualTo(now);
            assertThat(request.getReceiverHandoverAt()).isNull();
        }

        @Test
        @DisplayName(
                "Mũi tên 8b: LOGISTICS_CONFIRMED -> AWAITING_HANDOVER (Người nhận bấm trước kèm đánh giá đúng mô tả)")
        void logisticsConfirmedToAwaitingHandover_receiverFirst() {
            Request request = createRequest(RequestStatus.LOGISTICS_CONFIRMED);
            Instant now = Instant.now();

            // Người nhận bấm xác nhận đã nhận
            request.confirmHandover(false, true, now);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_HANDOVER);
            assertThat(request.getReceiverHandoverAt()).isEqualTo(now);
            assertThat(request.getAsDescribed()).isTrue();
            assertThat(request.getDonorHandoverAt()).isNull();
        }

        @Test
        @DisplayName("Mũi tên 8c: LOGISTICS_CONFIRMED -> AWAITING_HANDOVER (chuyển trực tiếp qua transitionTo())")
        void logisticsConfirmedToAwaitingHandover_directTransition() {
            Request request = createRequest(RequestStatus.LOGISTICS_CONFIRMED);
            request.transitionTo(RequestStatus.AWAITING_HANDOVER);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_HANDOVER);
        }

        @Test
        @DisplayName("Mũi tên 9: LOGISTICS_CONFIRMED -> CANCELLED (Huỷ khi đã chốt lịch nhưng chưa giao nhận)")
        void logisticsConfirmedToCancelled() {
            Request request = createRequest(RequestStatus.LOGISTICS_CONFIRMED);

            request.cancel("Hai bên thống nhất huỷ cuộc hẹn");

            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
            assertThat(request.getCancelReason()).isEqualTo("Hai bên thống nhất huỷ cuộc hẹn");
        }

        @Test
        @DisplayName("Mũi tên 9b: LOGISTICS_CONFIRMED -> CANCELLED (chuyển trực tiếp qua transitionTo())")
        void logisticsConfirmedToCancelled_directTransition() {
            Request request = createRequest(RequestStatus.LOGISTICS_CONFIRMED);
            request.transitionTo(RequestStatus.CANCELLED);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        }

        @Test
        @DisplayName("Mũi tên 10: AWAITING_HANDOVER -> COMPLETED (Bên thứ hai bấm xác nhận giao nhận thành công)")
        void awaitingHandoverToCompleted_bothConfirmed() {
            Request request = createRequest(RequestStatus.AWAITING_HANDOVER);
            Instant t1 = Instant.now();
            Instant t2 = t1.plus(1, ChronoUnit.HOURS);

            // Giả lập chủ bài đã xác nhận ở bước trước
            ReflectionTestUtils.setField(request, "donorHandoverAt", t1);

            // Người nhận xác nhận hoàn tất
            request.confirmHandover(false, true, t2);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
            assertThat(request.getCompletedAt()).isEqualTo(t2);
            assertThat(request.getAsDescribed()).isTrue();
        }

        @Test
        @DisplayName(
                "Mũi tên 10b: AWAITING_HANDOVER -> COMPLETED (Tự động hoàn tất sau 3 ngày bên kia im lặng qua autoConfirm())")
        void awaitingHandoverToCompleted_autoConfirm() {
            Request request = createRequest(RequestStatus.AWAITING_HANDOVER);
            Instant now = Instant.now();

            request.autoConfirm(now);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
            assertThat(request.isAutoConfirmed()).isTrue();
            assertThat(request.getCompletedAt()).isEqualTo(now);
            assertThat(request.getAsDescribed()).isTrue();
        }

        @Test
        @DisplayName("Mũi tên 10c: AWAITING_HANDOVER -> COMPLETED (chuyển trực tiếp qua transitionTo())")
        void awaitingHandoverToCompleted_directTransition() {
            Request request = createRequest(RequestStatus.AWAITING_HANDOVER);
            request.transitionTo(RequestStatus.COMPLETED);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        }

        @Test
        @DisplayName("Mũi tên 11: AWAITING_HANDOVER -> DISPUTED (Khiếu nại đồ không đúng mô tả hoặc sự cố giao nhận)")
        void awaitingHandoverToDisputed() {
            Request request = createRequest(RequestStatus.AWAITING_HANDOVER);

            request.transitionTo(RequestStatus.DISPUTED);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.DISPUTED);
        }

        @Test
        @DisplayName("Mũi tên 14: AWAITING_HANDOVER -> CANCELLED (Huỷ giao dịch khi một bên đã xác nhận giao nhận)")
        void awaitingHandoverToCancelled() {
            Request request = createRequest(RequestStatus.AWAITING_HANDOVER);

            request.cancel("Huỷ giao dịch");

            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        }

        @Test
        @DisplayName("Mũi tên 12: DISPUTED -> COMPLETED (Admin xử lý khiếu nại công nhận giao dịch thành công)")
        void disputedToCompleted() {
            Request request = createRequest(RequestStatus.DISPUTED);

            request.transitionTo(RequestStatus.COMPLETED);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        }

        @Test
        @DisplayName("Mũi tên 13: DISPUTED -> CANCELLED (Admin xử lý khiếu nại huỷ giao dịch)")
        void disputedToCancelled() {
            Request request = createRequest(RequestStatus.DISPUTED);

            request.cancel("Admin giải quyết tranh chấp: Huỷ giao dịch");

            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
            assertThat(request.getCancelReason()).isEqualTo("Admin giải quyết tranh chấp: Huỷ giao dịch");
        }

        @Test
        @DisplayName("Mũi tên 13b: DISPUTED -> CANCELLED (chuyển trực tiếp qua transitionTo())")
        void disputedToCancelled_directTransition() {
            Request request = createRequest(RequestStatus.DISPUTED);
            request.transitionTo(RequestStatus.CANCELLED);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("2. Kiểm thử tất cả các chuyển đổi không hợp lệ trên Request entity")
    class InvalidEntityTransitions {

        @ParameterizedTest(name = "[{index}] transitionTo từ {0} sang {1} phải ném REQUEST_INVALID_STATE_TRANSITION")
        @MethodSource("com.shareloop.request.RequestStateMachineTest#provideInvalidTransitions")
        @DisplayName("Tất cả các chuyển trạng thái không hợp lệ trong ma trận đều bị chặn và ném BusinessException")
        void allInvalidTransitionsThrowBusinessException(RequestStatus from, RequestStatus to) {
            Request request = createRequest(from);

            assertThatThrownBy(() -> request.transitionTo(to)).isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
            });

            // Trạng thái của entity không bị thay đổi sau khi bị từ chối
            assertThat(request.getStatus()).isEqualTo(from);
        }
    }

    @Nested
    @DisplayName("3. Kiểm thử các phương thức nghiệp vụ khi gọi ở trạng thái không hợp lệ")
    class InvalidDomainMethodCalls {

        @Test
        @DisplayName("select() chỉ được gọi khi PENDING; các trạng thái khác đều ném lỗi")
        void select_whenNotPending_throwsException() {
            for (RequestStatus status : RequestStatus.values()) {
                if (status != RequestStatus.PENDING) {
                    Request request = createRequest(status);
                    assertThatThrownBy(() -> request.select(Instant.now()))
                            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                                assertThat(ex.getErrorCode())
                                        .isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
                            });
                }
            }
        }

        @Test
        @DisplayName("reject() chỉ được gọi khi PENDING; các trạng thái khác đều ném lỗi")
        void reject_whenNotPending_throwsException() {
            for (RequestStatus status : RequestStatus.values()) {
                if (status != RequestStatus.PENDING) {
                    Request request = createRequest(status);
                    assertThatThrownBy(request::reject).isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
                    });
                }
            }
        }

        @Test
        @DisplayName("cancel() khi ở COMPLETED, REJECTED, CANCELLED đều ném lỗi")
        void cancel_whenNotAllowed_throwsException() {
            List<RequestStatus> cannotCancelStatuses =
                    List.of(RequestStatus.COMPLETED, RequestStatus.REJECTED, RequestStatus.CANCELLED);

            for (RequestStatus status : cannotCancelStatuses) {
                Request request = createRequest(status);
                assertThatThrownBy(() -> request.cancel("Huỷ không hợp lệ"))
                        .isInstanceOfSatisfying(BusinessException.class, ex -> {
                            assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
                        });
            }
        }

        @Test
        @DisplayName("BR-T05: proposeSchedule() bị khoá khi đã LOGISTICS_CONFIRMED trở lên (FR-83)")
        void proposeSchedule_whenLogisticsConfirmedOrLater_throwsScheduleLocked() {
            List<RequestStatus> lockedStatuses = List.of(
                    RequestStatus.LOGISTICS_CONFIRMED,
                    RequestStatus.AWAITING_HANDOVER,
                    RequestStatus.COMPLETED,
                    RequestStatus.DISPUTED,
                    RequestStatus.REJECTED,
                    RequestStatus.CANCELLED,
                    RequestStatus.PENDING);

            for (RequestStatus status : lockedStatuses) {
                Request request = createRequest(status);
                Instant meetingTime = Instant.now().plus(2, ChronoUnit.DAYS);
                assertThatThrownBy(() -> request.proposeSchedule(
                                "IN_PERSON", "Địa điểm", meetingTime, true, false, Instant.now()))
                        .isInstanceOfSatisfying(BusinessException.class, ex -> {
                            assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_SCHEDULE_LOCKED);
                        });
            }
        }

        @Test
        @DisplayName("confirmLogistics() khi không phải AWAITING_LOGISTICS đều ném lỗi")
        void confirmLogistics_whenNotAwaitingLogistics_throwsException() {
            for (RequestStatus status : RequestStatus.values()) {
                if (status != RequestStatus.AWAITING_LOGISTICS) {
                    Request request = createRequest(status);
                    assertThatThrownBy(() -> request.confirmLogistics(true, false, Instant.now()))
                            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                                assertThat(ex.getErrorCode())
                                        .isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
                            });
                }
            }
        }

        @Test
        @DisplayName("confirmLogistics() ném REQUEST_SCHEDULE_NOT_SET nếu chưa có meetingTime hoặc deliveryMethod")
        void confirmLogistics_whenScheduleNotSet_throwsException() {
            Request request = createRequest(RequestStatus.AWAITING_LOGISTICS);
            // Chưa gọi proposeSchedule() nên meetingTime và deliveryMethod đều null

            assertThatThrownBy(() -> request.confirmLogistics(true, false, Instant.now()))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_SCHEDULE_NOT_SET);
                    });
        }

        @Test
        @DisplayName("confirmLogistics() khi SHIPPING bắt buộc phải agreeToWaiver nếu chưa waiver (FR-80, BR-T03)")
        void confirmLogistics_shippingWithoutWaiver_throwsException() {
            Request request = createRequest(RequestStatus.AWAITING_LOGISTICS);
            Instant meetingTime = Instant.now().plus(2, ChronoUnit.DAYS);
            request.proposeSchedule("SHIPPING", null, meetingTime, false, false, Instant.now());

            // Bên cho xác nhận nhưng không chịu cam kết trách nhiệm waiver
            assertThatThrownBy(() -> request.confirmLogistics(true, false, Instant.now()))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_WAIVER_REQUIRED);
                    });
        }

        @Test
        @DisplayName("confirmHandover() khi không ở LOGISTICS_CONFIRMED hoặc AWAITING_HANDOVER đều ném lỗi")
        void confirmHandover_whenInvalidStatus_throwsException() {
            List<RequestStatus> invalidStatuses = List.of(
                    RequestStatus.PENDING,
                    RequestStatus.RESERVED,
                    RequestStatus.AWAITING_LOGISTICS,
                    RequestStatus.COMPLETED,
                    RequestStatus.DISPUTED,
                    RequestStatus.REJECTED,
                    RequestStatus.CANCELLED);

            for (RequestStatus status : invalidStatuses) {
                Request request = createRequest(status);
                assertThatThrownBy(() -> request.confirmHandover(true, null, Instant.now()))
                        .isInstanceOfSatisfying(BusinessException.class, ex -> {
                            assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
                        });
            }
        }

        @Test
        @DisplayName("confirmHandover() ném REQUEST_AS_DESCRIBED_REQUIRED khi người nhận không đánh giá asDescribed")
        void confirmHandover_whenReceiverMissingAsDescribed_throwsException() {
            Request request = createRequest(RequestStatus.LOGISTICS_CONFIRMED);

            assertThatThrownBy(() -> request.confirmHandover(false, null, Instant.now()))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_AS_DESCRIBED_REQUIRED);
                    });
        }

        @Test
        @DisplayName("confirmHandover() ném REQUEST_HANDOVER_ALREADY_CONFIRMED nếu một bên xác nhận lần thứ hai")
        void confirmHandover_whenAlreadyConfirmedBySameParty_throwsException() {
            Request request = createRequest(RequestStatus.LOGISTICS_CONFIRMED);
            Instant now = Instant.now();

            // Lần 1: Bên cho xác nhận thành công
            request.confirmHandover(true, null, now);

            // Lần 2: Bên cho bấm tiếp -> ném lỗi
            assertThatThrownBy(() -> request.confirmHandover(true, null, now.plusSeconds(10)))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_HANDOVER_ALREADY_CONFIRMED);
                    });
        }

        @Test
        @DisplayName(
                "autoConfirm() khi ở các trạng thái không thể sang COMPLETED đều ném REQUEST_INVALID_STATE_TRANSITION")
        void autoConfirm_whenCannotTransitionToCompleted_throwsException() {
            List<RequestStatus> cannotTransitionStatuses = List.of(
                    RequestStatus.PENDING,
                    RequestStatus.RESERVED,
                    RequestStatus.AWAITING_LOGISTICS,
                    RequestStatus.LOGISTICS_CONFIRMED,
                    RequestStatus.COMPLETED,
                    RequestStatus.REJECTED,
                    RequestStatus.CANCELLED);

            for (RequestStatus status : cannotTransitionStatuses) {
                Request request = createRequest(status);
                assertThatThrownBy(() -> request.autoConfirm(Instant.now()))
                        .isInstanceOfSatisfying(BusinessException.class, ex -> {
                            assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
                        });
            }
        }
    }

    @Nested
    @DisplayName("4. Kiểm thử các luồng vòng đời hoàn chỉnh (End-to-End Lifecycle Flows)")
    class EndToEndLifecycleFlows {

        @Test
        @DisplayName(
                "Vòng đời 1: Gặp trực tiếp thành công (PENDING -> RESERVED -> AWAITING_LOGISTICS -> LOGISTICS_CONFIRMED -> AWAITING_HANDOVER -> COMPLETED)")
        void lifecycle_directHandoverSuccess() {
            Request request = new Request(1L, 10L, null, "GIVE");
            assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING);

            // 1. Chủ bài chọn người nhận
            Instant t1 = Instant.now();
            request.select(t1);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.RESERVED);

            // 2. Người nhận đề xuất lịch gặp
            Instant meetingTime = t1.plus(3, ChronoUnit.DAYS);
            Instant t2 = t1.plus(1, ChronoUnit.HOURS);
            request.proposeSchedule("IN_PERSON", "Cổng FPT", meetingTime, false, false, t2);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_LOGISTICS);

            // 3. Chủ bài đồng ý chốt lịch -> chuyển sang LOGISTICS_CONFIRMED (BR-T05)
            Instant t3 = t2.plus(30, ChronoUnit.MINUTES);
            request.confirmLogistics(true, false, t3);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
            assertThat(request.getContactRevealedAt()).isEqualTo(t3);

            // 4. Chủ bài giao đồ xong bấm xác nhận -> chuyển sang AWAITING_HANDOVER
            Instant t4 = meetingTime.plus(10, ChronoUnit.MINUTES);
            request.confirmHandover(true, null, t4);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_HANDOVER);

            // 5. Người nhận kiểm tra đồ bấm xác nhận đúng mô tả -> chuyển sang COMPLETED
            Instant t5 = t4.plus(5, ChronoUnit.MINUTES);
            request.confirmHandover(false, true, t5);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
            assertThat(request.getCompletedAt()).isEqualTo(t5);
            assertThat(request.getAsDescribed()).isTrue();
        }

        @Test
        @DisplayName("Vòng đời 2: Giao hàng qua bưu điện + Waiver (SHIPPING) hoàn tất thành công")
        void lifecycle_shippingWaiverSuccess() {
            Request request = new Request(2L, 20L, null, "GIVE");

            // Chủ bài chọn người nhận
            request.select(Instant.now());

            // Chủ bài đề xuất ship và đồng ý cam kết miễn trừ trách nhiệm
            Instant meetingTime = Instant.now().plus(4, ChronoUnit.DAYS);
            Instant t1 = Instant.now();
            request.proposeSchedule("SHIPPING", null, meetingTime, true, true, t1);
            assertThat(request.getDonorWaiverAt()).isEqualTo(t1);

            // Người nhận xác nhận lịch và cũng đồng ý cam kết trách nhiệm
            Instant t2 = t1.plus(2, ChronoUnit.HOURS);
            request.confirmLogistics(false, true, t2);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.LOGISTICS_CONFIRMED);
            assertThat(request.getReceiverWaiverAt()).isEqualTo(t2);

            // Người nhận nhận được kiện hàng trước bấm xác nhận
            Instant t3 = meetingTime.plus(1, ChronoUnit.DAYS);
            request.confirmHandover(false, true, t3);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_HANDOVER);

            // Chủ bài xác nhận đã gửi thành công
            Instant t4 = t3.plus(1, ChronoUnit.HOURS);
            request.confirmHandover(true, null, t4);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        }

        @Test
        @DisplayName("Vòng đời 3: Tự động hoàn tất (autoConfirm) khi một bên im lặng quá 3 ngày (FR-87, BR-T06)")
        void lifecycle_autoConfirmAfterSilence() {
            Request request = new Request(3L, 30L, null, "GIVE");
            request.select(Instant.now());
            request.proposeSchedule(
                    "IN_PERSON", "Địa điểm", Instant.now().plus(2, ChronoUnit.DAYS), true, false, Instant.now());
            request.confirmLogistics(false, false, Instant.now());

            // Chủ bài xác nhận đã giao đồ
            Instant handoverTime = Instant.now();
            request.confirmHandover(true, null, handoverTime);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.AWAITING_HANDOVER);

            // Người nhận im lặng, 3 ngày sau job tự động hoàn tất
            Instant autoConfirmTime = handoverTime.plus(4, ChronoUnit.DAYS);
            request.autoConfirm(autoConfirmTime);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
            assertThat(request.isAutoConfirmed()).isTrue();
            assertThat(request.getCompletedAt()).isEqualTo(autoConfirmTime);
            assertThat(request.getAsDescribed()).isTrue();
        }

        @Test
        @DisplayName("Vòng đời 4: Tranh chấp giao nhận được Admin xử lý hoàn tất (DISPUTED -> COMPLETED)")
        void lifecycle_disputeResolvedToCompleted() {
            Request request = new Request(4L, 40L, null, "GIVE");
            request.select(Instant.now());
            request.proposeSchedule(
                    "IN_PERSON", "Địa điểm", Instant.now().plus(2, ChronoUnit.DAYS), true, false, Instant.now());
            request.confirmLogistics(false, false, Instant.now());
            request.confirmHandover(true, null, Instant.now());

            // Đồ có vấn đề -> chuyển sang DISPUTED
            request.transitionTo(RequestStatus.DISPUTED);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.DISPUTED);

            // Admin phân xử hai bên đồng thuận giải quyết -> hoàn tất
            request.transitionTo(RequestStatus.COMPLETED);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        }

        @Test
        @DisplayName("Vòng đời 5: Tranh chấp giao nhận được Admin xử lý huỷ (DISPUTED -> CANCELLED)")
        void lifecycle_disputeResolvedToCancelled() {
            Request request = new Request(5L, 50L, null, "GIVE");
            request.select(Instant.now());
            request.proposeSchedule(
                    "IN_PERSON", "Địa điểm", Instant.now().plus(2, ChronoUnit.DAYS), true, false, Instant.now());
            request.confirmLogistics(false, false, Instant.now());
            request.confirmHandover(true, null, Instant.now());

            // Chuyển sang DISPUTED
            request.transitionTo(RequestStatus.DISPUTED);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.DISPUTED);

            // Admin huỷ giao dịch
            request.cancel("Admin hủy giao dịch do hàng hỏng nặng");
            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
            assertThat(request.getCancelReason()).isEqualTo("Admin hủy giao dịch do hàng hỏng nặng");
        }
    }
}
