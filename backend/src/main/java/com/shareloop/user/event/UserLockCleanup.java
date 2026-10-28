package com.shareloop.user.event;

import java.util.ArrayList;
import java.util.List;

/**
 * Bộ đếm kết quả dọn dẹp khi khoá tài khoản. Các listener của module khác cộng dồn vào đây, rồi
 * AdminUserService ghi tổng vào activity log trong cùng transaction.
 */
public class UserLockCleanup {

    private int cancelledRequests;
    private long releasedCredit;
    private final List<Long> hiddenItemIds = new ArrayList<>();

    public void addCancelledRequests(int count) {
        this.cancelledRequests += count;
    }

    public void addReleasedCredit(long amount) {
        this.releasedCredit += amount;
    }

    public void addHiddenItemIds(List<Long> itemIds) {
        this.hiddenItemIds.addAll(itemIds);
    }

    public int getCancelledRequests() {
        return cancelledRequests;
    }

    public long getReleasedCredit() {
        return releasedCredit;
    }

    public List<Long> getHiddenItemIds() {
        return hiddenItemIds;
    }
}
