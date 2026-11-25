package com.shareloop.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.item.service.ItemService;
import com.shareloop.request.service.RequestService;
import com.shareloop.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Món đem đổi không còn dùng được thì các yêu cầu PENDING đang dùng món đó tự huỷ (YC-04, YC-07), chạy thật qua
 * ItemService, ItemUnavailableEvent và listener của module request.
 */
@Transactional
class OfferedItemUnavailableIT extends IntegrationTest {

    @Autowired
    RequestService requestService;

    @Autowired
    ItemService itemService;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EntityManager entityManager;

    @Test
    void selectReceiver_whenOfferedItemUsedInOtherPendingRequest_cancelsItOnceWithSingleNotice() {
        // given: B đem món X đổi lấy bài P của A (R1) và bài Q của C (R2)
        long donorA = user("offer-ua-a1@shareloop.local", "0900001101");
        long receiverB = user("offer-ua-b1@shareloop.local", "0900001102");
        long donorC = user("offer-ua-c1@shareloop.local", "0900001103");
        long itemP = swapItem(donorA);
        long itemQ = swapItem(donorC);
        long itemX = swapItem(receiverB);
        long requestR1 = pendingRequest(itemP, receiverB, itemX);
        long requestR2 = pendingRequest(itemQ, receiverB, itemX);

        // when: A chọn R1, món X bị giữ chỗ
        requestService.selectReceiver(requestR1, donorA);
        entityManager.flush();

        // then: R2 tự huỷ, B và chủ bài C mỗi người nhận đúng một thông báo huỷ, không ai nhận hai thông báo cho R2
        assertThat(requestStatus(requestR1)).isEqualTo("RESERVED");
        assertThat(requestStatus(requestR2)).isEqualTo("CANCELLED");
        assertThat(cancelNoticeCount(receiverB, requestR2)).isEqualTo(1);
        assertThat(cancelNoticeCount(donorC, requestR2)).isEqualTo(1);
        assertThat(maxCancelNoticesPerUser(requestR2)).isEqualTo(1);
    }

    @Test
    void selectReceiver_whenOfferedItemIsReservedAsMainItemElsewhere_cancelsRequestsUsingIt() {
        // given: B đem X đổi lấy Q của C (R2); đồng thời D xin chính bài X của B (R3)
        long receiverB = user("offer-ua-b2@shareloop.local", "0900001202");
        long donorC = user("offer-ua-c2@shareloop.local", "0900001203");
        long requesterD = user("offer-ua-d2@shareloop.local", "0900001204");
        long itemQ = swapItem(donorC);
        long itemX = swapItem(receiverB);
        long requestR2 = pendingRequest(itemQ, receiverB, itemX);
        long requestR3 = pendingRequest(itemX, requesterD, null);
        jdbc.sql("UPDATE requests SET type = 'GIVE' WHERE id = :id")
                .param("id", requestR3)
                .update();

        // when: B chọn D, bài X sang RESERVED ở giao dịch khác
        requestService.selectReceiver(requestR3, receiverB);
        entityManager.flush();

        // then: yêu cầu R2 đang dùng X tự huỷ, B và C đều được báo
        assertThat(requestStatus(requestR2)).isEqualTo("CANCELLED");
        assertThat(cancelNoticeCount(receiverB, requestR2)).isEqualTo(1);
        assertThat(cancelNoticeCount(donorC, requestR2)).isEqualTo(1);
    }

    @Test
    void hide_whenOfferedItemHidden_cancelsPendingRequestsUsingIt() {
        // given
        long receiverB = user("offer-ua-b3@shareloop.local", "0900001302");
        long donorC = user("offer-ua-c3@shareloop.local", "0900001303");
        long itemQ = swapItem(donorC);
        long itemX = swapItem(receiverB);
        long requestR2 = pendingRequest(itemQ, receiverB, itemX);

        // when
        itemService.hide(itemX);
        entityManager.flush();

        // then
        assertThat(requestStatus(requestR2)).isEqualTo("CANCELLED");
        assertThat(cancelNoticeCount(receiverB, requestR2)).isEqualTo(1);
    }

    private long user(String email, String phone) {
        return jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance) "
                        + "VALUES (:email, 'unused', 'Test', :phone, 'ACTIVE', 20) RETURNING id")
                .param("email", email)
                .param("phone", phone)
                .query(Long.class)
                .single();
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

    private String requestStatus(long requestId) {
        return jdbc.sql("SELECT status FROM requests WHERE id = :id")
                .param("id", requestId)
                .query(String.class)
                .single();
    }

    // Số thông báo huỷ nhiều nhất mà một người nhận được cho cùng một yêu cầu.
    private long maxCancelNoticesPerUser(long requestId) {
        return jdbc.sql("SELECT COALESCE(MAX(c), 0) FROM (SELECT COUNT(*) AS c FROM notifications"
                        + " WHERE type = 'REQUEST_CANCELLED' AND link = :link GROUP BY user_id) counts")
                .param("link", "/requests/" + requestId)
                .query(Long.class)
                .single();
    }

    private long cancelNoticeCount(long userId, long requestId) {
        return jdbc.sql("SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND type = 'REQUEST_CANCELLED'"
                        + " AND link = :link")
                .param("userId", userId)
                .param("link", "/requests/" + requestId)
                .query(Long.class)
                .single();
    }
}
