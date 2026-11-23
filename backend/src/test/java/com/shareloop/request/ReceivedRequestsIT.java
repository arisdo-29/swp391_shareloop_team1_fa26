package com.shareloop.request;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** G2: một lần gọi trả mọi yêu cầu tới các bài của người gọi, có lọc theo trạng thái. */
@AutoConfigureMockMvc
class ReceivedRequestsIT extends IntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> requestIds = new ArrayList<>();
    private final List<Long> itemIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        requestIds.forEach(id -> jdbc.update("DELETE FROM requests WHERE id = ?", id));
        itemIds.forEach(id -> jdbc.update("DELETE FROM items WHERE id = ?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM notifications WHERE user_id = ?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
        requestIds.clear();
        itemIds.clear();
        userIds.clear();
    }

    @Test
    void receivedRequests_returnsRequestsOfAllMyItemsAndFiltersByStatus() throws Exception {
        // given: chủ bài có hai bài, mỗi bài một yêu cầu (một PENDING, một RESERVED); người khác có yêu cầu riêng
        long donor = user();
        long receiver = user();
        long otherDonor = user();
        long itemA = item(donor);
        long itemB = item(donor);
        long otherItem = item(otherDonor);
        long pending = request(itemA, receiver, "PENDING");
        long reserved = request(itemB, receiver, "RESERVED");
        request(otherItem, receiver, "PENDING");
        String token = "Bearer " + jwt.issue(donor, false);

        // when, then: không lọc thì có đủ hai yêu cầu của mình, không lẫn yêu cầu của người khác
        mvc.perform(get("/api/v1/me/received-requests").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // when, then: lọc theo trạng thái
        mvc.perform(get("/api/v1/me/received-requests")
                        .param("status", "PENDING")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(pending));
        mvc.perform(get("/api/v1/me/received-requests")
                        .param("status", "RESERVED")
                        .param("status", "COMPLETED")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(reserved));
    }

    @Test
    void receivedRequests_withoutLogin_returnsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/me/received-requests")).andExpect(status().isUnauthorized());
    }

    private long user() {
        int seq = SEQ.incrementAndGet();
        Long id = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status)"
                        + " VALUES (?, 'hash', 'Received IT', ?, 'ACTIVE') RETURNING id",
                Long.class,
                "received-it-" + seq + "-" + System.nanoTime() + "@test.local",
                String.format("0992%06d", seq));
        userIds.add(id);
        return id;
    }

    private long item(long donor) {
        long categoryId = jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
        long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        Long id = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status,"
                        + " fee_state, pending_fee, pending_fee_type)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Bài nhận yêu cầu', 'Mô tả', 'GOOD', 'APPROVED', 'CHARGED', 0, NULL)"
                        + " RETURNING id",
                Long.class,
                donor,
                categoryId,
                areaId);
        itemIds.add(id);
        return id;
    }

    private long request(long itemId, long receiver, String status) {
        Long id = jdbc.queryForObject(
                "INSERT INTO requests (item_id, receiver_id, type, status, reserved_at)"
                        + " VALUES (?, ?, 'GIVE', ?, CASE WHEN ? = 'PENDING' THEN NULL ELSE now() END) RETURNING id",
                Long.class,
                itemId,
                receiver,
                status,
                status);
        requestIds.add(id);
        return id;
    }
}
