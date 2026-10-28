package com.shareloop.item.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.item.service.ItemService;
import com.shareloop.user.event.UserLockCleanup;
import com.shareloop.user.event.UserLockedEvent;
import com.shareloop.user.event.UserUnlockedEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserLockStateItemListenerTest {

    @Mock
    ItemService itemService;

    @InjectMocks
    UserLockStateItemListener listener;

    @Test
    void onUserLocked_hidesApprovedItemsAndRecordsTheirIds() {
        // given
        UserLockCleanup cleanup = new UserLockCleanup();
        when(itemService.hideApprovedItemsOfUser(10L)).thenReturn(List.of(1L, 2L));

        // when
        listener.onUserLocked(new UserLockedEvent(99L, 10L, cleanup));

        // then
        assertThat(cleanup.getHiddenItemIds()).containsExactly(1L, 2L);
    }

    @Test
    void onUserUnlocked_unhidesExactlyTheRecordedItems() {
        // when
        listener.onUserUnlocked(new UserUnlockedEvent(10L, List.of(1L, 2L)));

        // then
        verify(itemService).unhideItems(List.of(1L, 2L));
    }
}
