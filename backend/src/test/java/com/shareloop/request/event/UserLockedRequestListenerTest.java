package com.shareloop.request.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.shareloop.request.service.RequestService;
import com.shareloop.user.event.UserLockCleanup;
import com.shareloop.user.event.UserLockedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserLockedRequestListenerTest {

    @Mock
    RequestService requestService;

    @InjectMocks
    UserLockedRequestListener listener;

    @Test
    void onUserLocked_addsCancelledRequestCountToCleanup() {
        // given
        UserLockCleanup cleanup = new UserLockCleanup();
        when(requestService.cancelOpenRequestsOfLockedUser(10L)).thenReturn(3);

        // when
        listener.onUserLocked(new UserLockedEvent(1L, 10L, cleanup));

        // then
        assertThat(cleanup.getCancelledRequests()).isEqualTo(3);
    }
}
