package com.shareloop.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.request.dto.CreateRequest;
import com.shareloop.request.service.RequestService;
import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** C5 với database thật: chỉ lần tự huỷ của người nhận mới bắt chờ, huỷ do hệ thống thì không. */
@Transactional
class ResendCooldownIT extends IntegrationTest {

    @Autowired
    RequestService requestService;

    @Autowired
    JdbcClient jdbc;

    @Test
    void createRequest_whenSelfCancelledOneHourAgo_isBlockedByCooldown() {
        // given
        long[] ids = setup("cd1", "[Người nhận] Người gửi tự huỷ yêu cầu");

        // when
        Throwable error = catchThrowable(() -> requestService.createRequest(ids[0], ids[1], new CreateRequest(null)));

        // then
        assertThat(error).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_RESEND_COOLDOWN);
            assertThat(ex.getMessage()).contains("Có thể gửi lại sau");
        });
    }

    @Test
    void createRequest_whenCancelledBySystem_isNotBlockedByCooldown() {
        // given
        long[] ids = setup("cd2", "[Hệ thống] Bài đang tạm ẩn");

        // when
        var response = requestService.createRequest(ids[0], ids[1], new CreateRequest(null));

        // then
        assertThat(response.status()).isEqualTo("PENDING");
    }

    // Trả {id bài, id người nhận}. Có sẵn một yêu cầu CANCELLED của người nhận tới bài, cập nhật cách đây một giờ.
    private long[] setup(String prefix, String cancelReason) {
        long donorId = user(prefix + "-d@shareloop.local", "0907" + Math.abs(prefix.hashCode() % 100000) + "1");
        long receiverId = user(prefix + "-r@shareloop.local", "0907" + Math.abs(prefix.hashCode() % 100000) + "2");
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long itemId = jdbc.sql("INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, "
                        + "condition, status, approved_at, post_fee_paid, fee_state, free_edit_used) "
                        + "VALUES (:donorId, :categoryId, :areaId, 'GIVE', 'Bài được xin', 'Mô tả', 'GOOD', "
                        + "'APPROVED', now(), true, 'CHARGED', true) RETURNING id")
                .param("donorId", donorId)
                .param("categoryId", categoryId)
                .param("areaId", areaId)
                .query(Long.class)
                .single();
        long requestId = jdbc.sql("INSERT INTO requests (item_id, receiver_id, type, status, cancel_reason) "
                        + "VALUES (:itemId, :receiverId, 'GIVE', 'CANCELLED', :reason) RETURNING id")
                .param("itemId", itemId)
                .param("receiverId", receiverId)
                .param("reason", cancelReason)
                .query(Long.class)
                .single();
        // updated_at do trigger set_updated_at gán, nên đặt lại sau cùng bằng một lệnh riêng không đổi dữ liệu khác.
        jdbc.sql("ALTER TABLE requests DISABLE TRIGGER trg_requests_updated_at").update();
        jdbc.sql("UPDATE requests SET updated_at = now() - interval '1 hour' WHERE id = :id")
                .param("id", requestId)
                .update();
        jdbc.sql("ALTER TABLE requests ENABLE TRIGGER trg_requests_updated_at").update();
        return new long[] {itemId, receiverId};
    }

    private long user(String email, String phone) {
        return jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance) "
                        + "VALUES (:email, 'unused', 'Test', :phone, 'ACTIVE', 20) RETURNING id")
                .param("email", email)
                .param("phone", phone)
                .query(Long.class)
                .single();
    }
}
