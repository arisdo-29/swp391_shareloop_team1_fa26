package com.shareloop.item.event;

import com.shareloop.item.service.ItemService;
import com.shareloop.user.event.UserLockedEvent;
import com.shareloop.user.event.UserUnlockedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Ẩn bài của tài khoản bị khoá và hiện lại đúng những bài đó khi mở khoá. */
@Component
public class UserLockStateItemListener {

    private final ItemService itemService;

    public UserLockStateItemListener(ItemService itemService) {
        this.itemService = itemService;
    }

    // @Order lớn nhất để chạy sau listener của request: giao dịch bị huỷ nhả bài về APPROVED trước, rồi mới ẩn, nên
    // bài vừa được nhả cũng bị ẩn.
    @EventListener
    @Order(30)
    public void onUserLocked(UserLockedEvent event) {
        event.cleanup().addHiddenItemIds(itemService.hideApprovedItemsOfUser(event.userId()));
    }

    @EventListener
    public void onUserUnlocked(UserUnlockedEvent event) {
        itemService.unhideItems(event.hiddenItemIds());
    }
}
