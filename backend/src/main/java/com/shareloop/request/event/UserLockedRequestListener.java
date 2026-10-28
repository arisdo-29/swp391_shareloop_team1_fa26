package com.shareloop.request.event;

import com.shareloop.request.service.RequestService;
import com.shareloop.user.event.UserLockedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Huỷ các giao dịch đang mở của tài khoản vừa bị khoá. */
@Component
public class UserLockedRequestListener {

    private final RequestService requestService;

    public UserLockedRequestListener(RequestService requestService) {
        this.requestService = requestService;
    }

    // @EventListener thường: chạy cùng transaction với lúc khoá, lỗi thì cả việc khoá cũng rollback.
    @EventListener
    @Order(10)
    public void onUserLocked(UserLockedEvent event) {
        int cancelled = requestService.cancelOpenRequestsOfLockedUser(event.userId());
        event.cleanup().addCancelledRequests(cancelled);
    }
}
