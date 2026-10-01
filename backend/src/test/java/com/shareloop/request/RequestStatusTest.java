package com.shareloop.request;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shareloop.request.entity.RequestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test kiểm thử máy trạng thái Request (Spike S8, ERD 5.1).
 */
class RequestStatusTest {

    @Test
    @DisplayName("PENDING có thể chuyển sang RESERVED, REJECTED, CANCELLED")
    void pendingTransitions() {
        assertTrue(RequestStatus.canTransition(RequestStatus.PENDING, RequestStatus.RESERVED));
        assertTrue(RequestStatus.canTransition(RequestStatus.PENDING, RequestStatus.REJECTED));
        assertTrue(RequestStatus.canTransition(RequestStatus.PENDING, RequestStatus.CANCELLED));

        assertFalse(RequestStatus.canTransition(RequestStatus.PENDING, RequestStatus.COMPLETED));
        assertFalse(RequestStatus.canTransition(RequestStatus.PENDING, RequestStatus.AWAITING_LOGISTICS));
    }

    @Test
    @DisplayName("RESERVED có thể chuyển sang AWAITING_LOGISTICS hoặc CANCELLED")
    void reservedTransitions() {
        assertTrue(RequestStatus.canTransition(RequestStatus.RESERVED, RequestStatus.AWAITING_LOGISTICS));
        assertTrue(RequestStatus.canTransition(RequestStatus.RESERVED, RequestStatus.CANCELLED));

        assertFalse(RequestStatus.canTransition(RequestStatus.RESERVED, RequestStatus.PENDING));
        assertFalse(RequestStatus.canTransition(RequestStatus.RESERVED, RequestStatus.COMPLETED));
    }

    @Test
    @DisplayName("AWAITING_LOGISTICS có thể chuyển sang LOGISTICS_CONFIRMED hoặc CANCELLED")
    void awaitingLogisticsTransitions() {
        assertTrue(RequestStatus.canTransition(RequestStatus.AWAITING_LOGISTICS, RequestStatus.LOGISTICS_CONFIRMED));
        assertTrue(RequestStatus.canTransition(RequestStatus.AWAITING_LOGISTICS, RequestStatus.CANCELLED));

        assertFalse(RequestStatus.canTransition(RequestStatus.AWAITING_LOGISTICS, RequestStatus.COMPLETED));
    }

    @Test
    @DisplayName("LOGISTICS_CONFIRMED có thể chuyển sang AWAITING_HANDOVER hoặc CANCELLED")
    void logisticsConfirmedTransitions() {
        assertTrue(RequestStatus.canTransition(RequestStatus.LOGISTICS_CONFIRMED, RequestStatus.AWAITING_HANDOVER));
        assertTrue(RequestStatus.canTransition(RequestStatus.LOGISTICS_CONFIRMED, RequestStatus.CANCELLED));

        assertFalse(RequestStatus.canTransition(RequestStatus.LOGISTICS_CONFIRMED, RequestStatus.PENDING));
    }

    @Test
    @DisplayName("AWAITING_HANDOVER có thể chuyển sang COMPLETED hoặc DISPUTED")
    void awaitingHandoverTransitions() {
        assertTrue(RequestStatus.canTransition(RequestStatus.AWAITING_HANDOVER, RequestStatus.COMPLETED));
        assertTrue(RequestStatus.canTransition(RequestStatus.AWAITING_HANDOVER, RequestStatus.DISPUTED));

        assertFalse(RequestStatus.canTransition(RequestStatus.AWAITING_HANDOVER, RequestStatus.CANCELLED));
    }

    @Test
    @DisplayName("DISPUTED có thể chuyển sang COMPLETED hoặc CANCELLED sau khi Admin xử lý")
    void disputedTransitions() {
        assertTrue(RequestStatus.canTransition(RequestStatus.DISPUTED, RequestStatus.COMPLETED));
        assertTrue(RequestStatus.canTransition(RequestStatus.DISPUTED, RequestStatus.CANCELLED));

        assertFalse(RequestStatus.canTransition(RequestStatus.DISPUTED, RequestStatus.PENDING));
    }

    @Test
    @DisplayName("Các trạng thái kết thúc (COMPLETED, REJECTED, CANCELLED) không thể chuyển đi đâu")
    void terminalStatesCannotTransition() {
        for (RequestStatus target : RequestStatus.values()) {
            assertFalse(RequestStatus.canTransition(RequestStatus.COMPLETED, target));
            assertFalse(RequestStatus.canTransition(RequestStatus.REJECTED, target));
            assertFalse(RequestStatus.canTransition(RequestStatus.CANCELLED, target));
        }
    }

    @Test
    @DisplayName("Chuyển sang chính nó hoặc null luôn trả về false")
    void selfOrNullTransitionIsInvalid() {
        assertFalse(RequestStatus.canTransition(RequestStatus.PENDING, RequestStatus.PENDING));
        assertFalse(RequestStatus.canTransition(null, RequestStatus.PENDING));
        assertFalse(RequestStatus.canTransition(RequestStatus.PENDING, null));
    }
}
