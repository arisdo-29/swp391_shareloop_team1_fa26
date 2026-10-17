package com.shareloop.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shareloop.request.entity.RequestStatus;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit test kiểm thử toàn diện máy trạng thái RequestStatus (ERD 5.1, Hình 21; Spike S8; Issue #127).
 * Kiểm tra đầy đủ 13 mũi tên hợp lệ và toàn bộ các chuyển trạng thái không hợp lệ trong ma trận 81 ô.
 */
class RequestStatusTest {

    // Tập hợp 13 mũi tên hợp lệ theo sơ đồ vòng đời Request (ERD 5.1)
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
            "DISPUTED->COMPLETED",
            "DISPUTED->CANCELLED");

    private static String key(RequestStatus from, RequestStatus to) {
        return from.name() + "->" + to.name();
    }

    static Stream<Arguments> provideValidTransitions() {
        return Stream.of(
                Arguments.of(RequestStatus.PENDING, RequestStatus.RESERVED, "Chủ bài chọn người nhận (FR-66, BR-D01)"),
                Arguments.of(RequestStatus.PENDING, RequestStatus.REJECTED, "Chủ bài từ chối/chọn người khác (BR-D01)"),
                Arguments.of(
                        RequestStatus.PENDING,
                        RequestStatus.CANCELLED,
                        "Người gửi tự huỷ hoặc job huỷ treo sau 3 ngày (BR-R04, FR-119, BR-R06)"),
                Arguments.of(
                        RequestStatus.RESERVED,
                        RequestStatus.AWAITING_LOGISTICS,
                        "Đề xuất lịch hẹn giao nhận (FR-79, FR-80, BR-T01, BR-T02)"),
                Arguments.of(
                        RequestStatus.RESERVED,
                        RequestStatus.CANCELLED,
                        "Quá 10 ngày không chốt lịch (FR-120, BR-T10) hoặc huỷ giao dịch"),
                Arguments.of(
                        RequestStatus.AWAITING_LOGISTICS,
                        RequestStatus.LOGISTICS_CONFIRMED,
                        "Cả hai bên chốt lịch hẹn (FR-80, FR-81, BR-T03, BR-T05)"),
                Arguments.of(
                        RequestStatus.AWAITING_LOGISTICS,
                        RequestStatus.CANCELLED,
                        "Quá 10 ngày không chốt lịch (FR-120, BR-T10) hoặc huỷ giao dịch"),
                Arguments.of(
                        RequestStatus.LOGISTICS_CONFIRMED,
                        RequestStatus.AWAITING_HANDOVER,
                        "Một bên xác nhận đã giao/nhận (FR-84, FR-85, BR-D02, UC-27)"),
                Arguments.of(
                        RequestStatus.LOGISTICS_CONFIRMED,
                        RequestStatus.CANCELLED,
                        "Huỷ giao dịch đã chốt lịch do sự cố phát sinh trước khi gặp"),
                Arguments.of(
                        RequestStatus.AWAITING_HANDOVER,
                        RequestStatus.COMPLETED,
                        "Cả hai bên xác nhận hoặc tự hoàn tất sau 3 ngày (FR-84, FR-87, BR-T06, BR-R03)"),
                Arguments.of(
                        RequestStatus.AWAITING_HANDOVER,
                        RequestStatus.DISPUTED,
                        "Không đúng mô tả hoặc phát sinh tranh chấp giao nhận (FR-88, BR-T07, UC-28, F16)"),
                Arguments.of(
                        RequestStatus.DISPUTED,
                        RequestStatus.COMPLETED,
                        "Admin phân xử giải quyết cho hoàn tất giao dịch (F16, Hình 19)"),
                Arguments.of(
                        RequestStatus.DISPUTED,
                        RequestStatus.CANCELLED,
                        "Admin phân xử huỷ giao dịch và hoàn trả (F16, Hình 19)"));
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
    @DisplayName("1. Mỗi mũi tên hợp lệ có một test riêng biệt")
    class ValidTransitionTests {

        @Test
        @DisplayName("Mũi tên 1: PENDING -> RESERVED khi chủ bài chọn người nhận (FR-66, BR-D01)")
        void transition_Pending_To_Reserved() {
            assertTrue(RequestStatus.canTransition(RequestStatus.PENDING, RequestStatus.RESERVED));
        }

        @Test
        @DisplayName("Mũi tên 2: PENDING -> REJECTED khi chủ bài từ chối hoặc chọn người khác (BR-D01)")
        void transition_Pending_To_Rejected() {
            assertTrue(RequestStatus.canTransition(RequestStatus.PENDING, RequestStatus.REJECTED));
        }

        @Test
        @DisplayName(
                "Mũi tên 3: PENDING -> CANCELLED khi người gửi tự huỷ hoặc job huỷ treo quá 3 ngày (BR-R04, FR-119, BR-R06)")
        void transition_Pending_To_Cancelled() {
            assertTrue(RequestStatus.canTransition(RequestStatus.PENDING, RequestStatus.CANCELLED));
        }

        @Test
        @DisplayName(
                "Mũi tên 4: RESERVED -> AWAITING_LOGISTICS khi đề xuất lịch hẹn giao nhận (FR-79, FR-80, BR-T01, BR-T02)")
        void transition_Reserved_To_AwaitingLogistics() {
            assertTrue(RequestStatus.canTransition(RequestStatus.RESERVED, RequestStatus.AWAITING_LOGISTICS));
        }

        @Test
        @DisplayName(
                "Mũi tên 5: RESERVED -> CANCELLED khi quá 10 ngày không chốt lịch (FR-120, BR-T10) hoặc huỷ giao dịch")
        void transition_Reserved_To_Cancelled() {
            assertTrue(RequestStatus.canTransition(RequestStatus.RESERVED, RequestStatus.CANCELLED));
        }

        @Test
        @DisplayName(
                "Mũi tên 6: AWAITING_LOGISTICS -> LOGISTICS_CONFIRMED khi hai bên xác nhận lịch và cam kết (FR-80, FR-81, BR-T03, BR-T05)")
        void transition_AwaitingLogistics_To_LogisticsConfirmed() {
            assertTrue(
                    RequestStatus.canTransition(RequestStatus.AWAITING_LOGISTICS, RequestStatus.LOGISTICS_CONFIRMED));
        }

        @Test
        @DisplayName(
                "Mũi tên 7: AWAITING_LOGISTICS -> CANCELLED khi quá 10 ngày không chốt lịch (FR-120, BR-T10) hoặc huỷ giao dịch")
        void transition_AwaitingLogistics_To_Cancelled() {
            assertTrue(RequestStatus.canTransition(RequestStatus.AWAITING_LOGISTICS, RequestStatus.CANCELLED));
        }

        @Test
        @DisplayName(
                "Mũi tên 8: LOGISTICS_CONFIRMED -> AWAITING_HANDOVER khi một bên xác nhận đã giao/nhận (FR-84, FR-85, BR-D02, UC-27)")
        void transition_LogisticsConfirmed_To_AwaitingHandover() {
            assertTrue(RequestStatus.canTransition(RequestStatus.LOGISTICS_CONFIRMED, RequestStatus.AWAITING_HANDOVER));
        }

        @Test
        @DisplayName(
                "Mũi tên 9: LOGISTICS_CONFIRMED -> CANCELLED khi huỷ giao dịch đã chốt lịch do sự cố phát sinh trước khi gặp")
        void transition_LogisticsConfirmed_To_Cancelled() {
            assertTrue(RequestStatus.canTransition(RequestStatus.LOGISTICS_CONFIRMED, RequestStatus.CANCELLED));
        }

        @Test
        @DisplayName(
                "Mũi tên 10: AWAITING_HANDOVER -> COMPLETED khi cả 2 bên xác nhận hoặc tự hoàn tất sau 3 ngày (FR-84, FR-87, BR-T06, BR-R03)")
        void transition_AwaitingHandover_To_Completed() {
            assertTrue(RequestStatus.canTransition(RequestStatus.AWAITING_HANDOVER, RequestStatus.COMPLETED));
        }

        @Test
        @DisplayName(
                "Mũi tên 11: AWAITING_HANDOVER -> DISPUTED khi đồ không đúng mô tả hoặc có khiếu nại giao nhận (FR-88, BR-T07, UC-28, F16)")
        void transition_AwaitingHandover_To_Disputed() {
            assertTrue(RequestStatus.canTransition(RequestStatus.AWAITING_HANDOVER, RequestStatus.DISPUTED));
        }

        @Test
        @DisplayName(
                "Mũi tên 12: DISPUTED -> COMPLETED khi Admin phân xử công nhận giao dịch thành công (F16, Hình 19)")
        void transition_Disputed_To_Completed() {
            assertTrue(RequestStatus.canTransition(RequestStatus.DISPUTED, RequestStatus.COMPLETED));
        }

        @Test
        @DisplayName("Mũi tên 13: DISPUTED -> CANCELLED khi Admin phân xử huỷ giao dịch và hoàn trả đồ (F16, Hình 19)")
        void transition_Disputed_To_Cancelled() {
            assertTrue(RequestStatus.canTransition(RequestStatus.DISPUTED, RequestStatus.CANCELLED));
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}: {2}")
        @MethodSource("com.shareloop.request.RequestStatusTest#provideValidTransitions")
        @DisplayName("Tất cả 13 mũi tên hợp lệ đều trả về true")
        void allValidTransitionsReturnTrue(RequestStatus from, RequestStatus to, String description) {
            assertThat(RequestStatus.canTransition(from, to))
                    .as("Chuyển từ %s sang %s phải hợp lệ (%s)", from, to, description)
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("2. Kiểm thử tất cả các chuyển đổi không hợp lệ trong ma trận 81 ô")
    class InvalidTransitionTests {

        @ParameterizedTest(name = "[{index}] {0} -> {1} phải bị từ chối")
        @MethodSource("com.shareloop.request.RequestStatusTest#provideInvalidTransitions")
        @DisplayName("Tất cả 68 chuyển đổi khác trạng thái và 9 chuyển đổi sang chính nó đều trả về false")
        void allInvalidTransitionsReturnFalse(RequestStatus from, RequestStatus to) {
            assertThat(RequestStatus.canTransition(from, to))
                    .as("Chuyển từ %s sang %s không hợp lệ và phải trả về false", from, to)
                    .isFalse();
        }

        @Test
        @DisplayName("Tổng số mũi tên hợp lệ phải đúng bằng 13 và số không hợp lệ là 68 + 9 = 77")
        void transitionMatrixTotals() {
            int validCount = 0;
            int invalidCount = 0;
            for (RequestStatus from : RequestStatus.values()) {
                for (RequestStatus to : RequestStatus.values()) {
                    if (RequestStatus.canTransition(from, to)) {
                        validCount++;
                    } else {
                        invalidCount++;
                    }
                }
            }
            assertThat(validCount).as("Số mũi tên hợp lệ phải đúng bằng 13").isEqualTo(13);
            assertThat(invalidCount)
                    .as("Số ô không hợp lệ phải đúng bằng 81 - 13 = 68")
                    .isEqualTo(68);
        }
    }

    @Nested
    @DisplayName("3. Kiểm thử các trạng thái kết thúc (Terminal states)")
    class TerminalStateTests {

        @Test
        @DisplayName("COMPLETED không thể chuyển sang bất kỳ trạng thái nào")
        void completedIsTerminal() {
            for (RequestStatus target : RequestStatus.values()) {
                assertFalse(RequestStatus.canTransition(RequestStatus.COMPLETED, target));
            }
        }

        @Test
        @DisplayName("REJECTED không thể chuyển sang bất kỳ trạng thái nào")
        void rejectedIsTerminal() {
            for (RequestStatus target : RequestStatus.values()) {
                assertFalse(RequestStatus.canTransition(RequestStatus.REJECTED, target));
            }
        }

        @Test
        @DisplayName("CANCELLED không thể chuyển sang bất kỳ trạng thái nào")
        void cancelledIsTerminal() {
            for (RequestStatus target : RequestStatus.values()) {
                assertFalse(RequestStatus.canTransition(RequestStatus.CANCELLED, target));
            }
        }
    }

    @Nested
    @DisplayName("4. Kiểm thử các trường hợp null và tự chuyển đổi sang chính mình")
    class EdgeCaseTests {

        @Test
        @DisplayName("Chuyển từ null sang bất kỳ trạng thái nào luôn trả về false")
        void nullFromTransitionIsInvalid() {
            for (RequestStatus to : RequestStatus.values()) {
                assertFalse(RequestStatus.canTransition(null, to));
            }
        }

        @Test
        @DisplayName("Chuyển từ bất kỳ trạng thái nào sang null luôn trả về false")
        void nullToTransitionIsInvalid() {
            for (RequestStatus from : RequestStatus.values()) {
                assertFalse(RequestStatus.canTransition(from, null));
            }
        }

        @Test
        @DisplayName("canTransition(null, null) luôn trả về false")
        void nullNullTransitionIsInvalid() {
            assertFalse(RequestStatus.canTransition(null, null));
        }

        @Test
        @DisplayName("Chuyển sang chính nó luôn trả về false cho tất cả 9 trạng thái")
        void selfTransitionsAreInvalid() {
            for (RequestStatus status : EnumSet.allOf(RequestStatus.class)) {
                assertFalse(RequestStatus.canTransition(status, status));
            }
        }
    }
}
