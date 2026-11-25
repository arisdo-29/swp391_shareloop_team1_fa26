package com.shareloop.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.request.dto.AdminRequestPageResponse;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.service.AdminRequestService;
import com.shareloop.support.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * G2: trang giao dịch của Admin lọc, sắp xếp và chia trang trong SQL. Kiểm kết quả theo cách tính trong bộ nhớ trước đây
 * (danh sách đầy đủ, mới nhất trước, lọc theo từng điều kiện) trên dữ liệu thật.
 */
class AdminRequestListIT extends IntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    AdminRequestService adminRequestService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> requestIds = new ArrayList<>();
    private final List<Long> itemIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private long idGive;
    private long idSwapShipping;
    private long idGiveShipping;
    private long idOld;

    @AfterEach
    void cleanUp() {
        requestIds.forEach(id -> jdbc.update("DELETE FROM requests WHERE id = ?", id));
        itemIds.forEach(id -> jdbc.update("DELETE FROM items WHERE id = ?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
        requestIds.clear();
        itemIds.clear();
        userIds.clear();
    }

    private void givenFourRequests() {
        // cũ nhất tới mới nhất: idOld (10 ngày trước), idGive (3), idSwapShipping (2), idGiveShipping (1)
        idOld = insertRequest("GIVE", "IN_PERSON", "RESERVED", 10);
        idGive = insertRequest("GIVE", "IN_PERSON", "RESERVED", 3);
        idSwapShipping = insertRequest("SWAP", "SHIPPING", "AWAITING_HANDOVER", 2);
        idGiveShipping = insertRequest("GIVE", "SHIPPING", "RESERVED", 1);
    }

    @Test
    void list_filtersByTypeDeliveryStatusAndOrdersNewestFirst() {
        // given
        givenFourRequests();

        // when
        AdminRequestPageResponse swaps = adminRequestService.list(null, null, null, "SWAP", null, null, null, 0, 50);
        AdminRequestPageResponse shipping =
                adminRequestService.list(null, null, null, null, "SHIPPING", null, null, 0, 50);
        AdminRequestPageResponse giveShipping =
                adminRequestService.list(null, null, null, "GIVE", "SHIPPING", null, null, 0, 50);
        AdminRequestPageResponse awaiting =
                adminRequestService.list(RequestStatus.AWAITING_HANDOVER, null, null, null, null, null, null, 0, 50);

        // then
        assertThat(ids(swaps)).containsExactly(idSwapShipping);
        assertThat(ids(shipping)).containsExactly(idGiveShipping, idSwapShipping);
        assertThat(ids(giveShipping)).containsExactly(idGiveShipping);
        assertThat(ids(awaiting)).containsExactly(idSwapShipping);
    }

    @Test
    void list_filtersByCreatedDateRangeInVietnamTime() {
        // given
        givenFourRequests();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        // when: 5 ngày gần nhất, và chỉ tới trước 5 ngày
        AdminRequestPageResponse recent =
                adminRequestService.list(null, null, null, null, null, today.minusDays(5), today, 0, 50);
        AdminRequestPageResponse older =
                adminRequestService.list(null, null, null, null, null, null, today.minusDays(5), 0, 50);

        // then
        assertThat(ids(recent)).containsExactly(idGiveShipping, idSwapShipping, idGive);
        assertThat(ids(older)).containsExactly(idOld);
    }

    @Test
    void list_paginatesInSqlWithSameOrderAndCorrectTotals() {
        // given
        givenFourRequests();

        // when: lọc theo ngày để chỉ còn bốn dòng của test
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        AdminRequestPageResponse first =
                adminRequestService.list(null, null, null, null, null, today.minusDays(11), today, 0, 2);
        AdminRequestPageResponse second =
                adminRequestService.list(null, null, null, null, null, today.minusDays(11), today, 1, 2);

        // then: hai trang nối lại đúng danh sách đầy đủ mới nhất trước
        assertThat(ids(first)).containsExactly(idGiveShipping, idSwapShipping);
        assertThat(ids(second)).containsExactly(idGive, idOld);
        assertThat(first.totalElements()).isEqualTo(4);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(second.page()).isEqualTo(1);
    }

    private static List<Long> ids(AdminRequestPageResponse page) {
        return page.items().stream().map(row -> row.id()).toList();
    }

    private long insertRequest(String type, String delivery, String status, int daysAgo) {
        int seq = SEQ.incrementAndGet();
        Long donor = insertUser("admin-list-" + seq, String.format("0994%06d", seq));
        Long receiver = insertUser("admin-list-r-" + seq, String.format("0993%06d", seq));
        long categoryId = jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
        long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        Long itemId = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, desired_item,"
                        + " condition, status, fee_state, pending_fee, pending_fee_type)"
                        + " VALUES (?, ?, ?, ?, 'Bài admin list', 'Mô tả', ?, 'GOOD', 'RESERVED', 'CHARGED', 0, NULL)"
                        + " RETURNING id",
                Long.class,
                donor,
                categoryId,
                areaId,
                type,
                "SWAP".equals(type) ? "Muốn đổi" : null);
        itemIds.add(itemId);
        Long requestId = jdbc.queryForObject(
                "INSERT INTO requests (item_id, receiver_id, type, status, delivery_method, reserved_at, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, now(), now() - (? || ' days')::interval) RETURNING id",
                Long.class,
                itemId,
                receiver,
                type,
                status,
                delivery,
                String.valueOf(daysAgo));
        requestIds.add(requestId);
        return requestId;
    }

    private Long insertUser(String emailPrefix, String phone) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status)"
                        + " VALUES (?, 'hash', 'Admin list IT', ?, 'ACTIVE') RETURNING id",
                Long.class,
                emailPrefix + "-" + System.nanoTime() + "@test.local",
                phone);
        userIds.add(id);
        return id;
    }
}
