package com.shareloop.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.request.service.RequestService;
import com.shareloop.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * C3: bấm cùng lúc không gây lỗi 500. Không dùng @Transactional ở lớp test vì cần hai transaction thật chạy song
 * song, nên dữ liệu tự tạo được dọn trong @AfterEach.
 */
class SelectReceiverConcurrencyIT extends IntegrationTest {

    @Autowired
    RequestService requestService;

    @Autowired
    JdbcClient jdbc;

    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long userId : userIds) {
            jdbc.sql("DELETE FROM notifications WHERE user_id = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM requests WHERE receiver_id = :id OR item_id IN"
                            + " (SELECT id FROM items WHERE donor_id = :id)")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM items WHERE donor_id = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM users WHERE id = :id").param("id", userId).update();
        }
        userIds.clear();
    }

    @Test
    void selectReceiver_whenTwoDonorsPickSameOfferedItemAtOnce_onlyOneSucceeds() throws Exception {
        // given: B đem cùng món X đổi lấy bài P của A (R1) và bài Q của C (R2)
        long donorA = user("c3-a@shareloop.local", "0906000001");
        long donorC = user("c3-c@shareloop.local", "0906000002");
        long receiverB = user("c3-b@shareloop.local", "0906000003");
        long itemP = swapItem(donorA);
        long itemQ = swapItem(donorC);
        long itemX = swapItem(receiverB);
        long requestR1 = pendingRequest(itemP, receiverB, itemX);
        long requestR2 = pendingRequest(itemQ, receiverB, itemX);

        // when: A và C cùng chọn B ở cùng một lúc
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Throwable> first = pool.submit(() -> runAfterSignal(ready, go, requestR1, donorA));
        Future<Throwable> second = pool.submit(() -> runAfterSignal(ready, go, requestR2, donorC));
        ready.await();
        go.countDown();
        Throwable firstError = first.get();
        Throwable secondError = second.get();
        pool.shutdown();

        // then: đúng một luồng thành công, luồng còn lại nhận lỗi nghiệp vụ rõ ràng (không phải lỗi 500)
        int failures = (firstError == null ? 0 : 1) + (secondError == null ? 0 : 1);
        assertThat(failures).isEqualTo(1);
        Throwable loser = firstError != null ? firstError : secondError;
        assertThat(loser).isInstanceOf(BusinessException.class);
        assertThat(reservedRequestsUsingItem(itemX)).isEqualTo(1);
    }

    @Test
    void selectReceiver_whenSameRequestSelectedTwice_secondCallGetsConflictBusinessError() {
        // given
        long donorA = user("c3-a2@shareloop.local", "0906000011");
        long receiverB = user("c3-b2@shareloop.local", "0906000012");
        long itemP = swapItem(donorA);
        long requestR1 = pendingRequest(itemP, receiverB, null);
        requestService.selectReceiver(requestR1, donorA);

        // when
        Throwable second = catchThrowable(() -> requestService.selectReceiver(requestR1, donorA));

        // then
        assertThat(second)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.getErrorCode().status().value())
                                .isEqualTo(409));
    }

    private Throwable runAfterSignal(CountDownLatch ready, CountDownLatch go, long requestId, long donorId)
            throws InterruptedException {
        ready.countDown();
        go.await();
        return catchThrowable(() -> requestService.selectReceiver(requestId, donorId));
    }

    private long reservedRequestsUsingItem(long itemId) {
        return jdbc.sql("SELECT count(*) FROM requests WHERE offered_item_id = :id AND status = 'RESERVED'")
                .param("id", itemId)
                .query(Long.class)
                .single();
    }

    private long user(String email, String phone) {
        long id = jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance) "
                        + "VALUES (:email, 'unused', 'Test', :phone, 'ACTIVE', 50) RETURNING id")
                .param("email", email)
                .param("phone", phone)
                .query(Long.class)
                .single();
        userIds.add(id);
        return id;
    }

    private long swapItem(long ownerId) {
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        return jdbc.sql("INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, "
                        + "desired_item, condition, status, approved_at, post_fee_paid, fee_state, free_edit_used) "
                        + "VALUES (:ownerId, :categoryId, :areaId, 'SWAP', 'Món đổi', 'Mô tả', 'Sách', 'GOOD', "
                        + "'APPROVED', now(), true, 'CHARGED', true) RETURNING id")
                .param("ownerId", ownerId)
                .param("categoryId", categoryId)
                .param("areaId", areaId)
                .query(Long.class)
                .single();
    }

    private long pendingRequest(long itemId, long receiverId, Long offeredItemId) {
        return jdbc.sql("INSERT INTO requests (item_id, receiver_id, offered_item_id, type, status) "
                        + "VALUES (:itemId, :receiverId, :offeredItemId, 'SWAP', 'PENDING') RETURNING id")
                .param("itemId", itemId)
                .param("receiverId", receiverId)
                .param("offeredItemId", offeredItemId)
                .query(Long.class)
                .single();
    }
}
