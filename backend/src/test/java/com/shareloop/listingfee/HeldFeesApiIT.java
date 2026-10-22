package com.shareloop.listingfee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

/**
 * GET /listing-fees/held (FR-15) trên PostgreSQL thật: dữ liệu được chèn thẳng bằng SQL ở trạng thái "đang giữ phí"
 * rồi gọi API qua MockMvc. Dữ liệu do test tạo được dọn trong @AfterEach.
 */
@AutoConfigureMockMvc
class HeldFeesApiIT extends IntegrationTest {

    private static final String URL = "/api/v1/listing-fees/held";
    private static final int FEE = 5;
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();

    // items tham chiếu users nên xoá items trước.
    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM items WHERE donor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUserIds.clear();
    }

    @Test
    void getHeldFees_whenPostAndEditFeeHeld_returnsTwoRowsWithRightFeeType() throws Exception {
        // given: một bài SWAP chờ duyệt giữ phí đăng, một bài đã duyệt chờ duyệt bản sửa giữ phí sửa
        long userId = insertUser(FEE * 2);
        long postItemId = insertItem(userId, "SWAP", "PENDING_REVIEW", "HELD", FEE, "POST");
        long editItemId = insertItem(userId, "SWAP", "APPROVED", "HELD", FEE, "EDIT");
        String token = jwtService.issue(userId, false);

        // when + then
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(
                        jsonPath("$[?(@.itemId == " + postItemId + ")].feeType").value("POST"))
                .andExpect(
                        jsonPath("$[?(@.itemId == " + editItemId + ")].feeType").value("EDIT"))
                .andExpect(jsonPath("$[?(@.itemId == " + editItemId + ")].pendingFee")
                        .value(FEE))
                .andExpect(jsonPath("$[?(@.itemId == " + postItemId + ")].offerType")
                        .value("SWAP"));
    }

    @Test
    void getHeldFees_whenListed_totalPendingFeeEqualsHeldCredit() throws Exception {
        // given
        long userId = insertUser(FEE * 2);
        insertItem(userId, "SWAP", "PENDING_REVIEW", "HELD", FEE, "POST");
        insertItem(userId, "SWAP", "APPROVED", "HELD", FEE, "EDIT");
        String token = jwtService.issue(userId, false);

        // when
        String body = mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then: tổng pendingFee của các dòng bằng users.held_credit
        List<Integer> pendingFees = JsonPath.read(body, "$[*].pendingFee");
        int total = 0;
        for (Integer pendingFee : pendingFees) {
            total += pendingFee;
        }
        Integer heldCredit = jdbc.queryForObject("SELECT held_credit FROM users WHERE id = ?", Integer.class, userId);
        assertThat(total).isEqualTo(heldCredit);
    }

    @Test
    void getHeldFees_whenItemIsGiveWithNoFeeHeld_doesNotListIt() throws Exception {
        // given: bài GIVE miễn phí đăng nên không giữ gì
        long userId = insertUser(0);
        insertItem(userId, "GIVE", "PENDING_REVIEW", "NONE", 0, null);
        String token = jwtService.issue(userId, false);

        // when + then
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getHeldFees_whenFeeAlreadyChargedOrReleased_doesNotListIt() throws Exception {
        // given: một bài đã duyệt xong (phí đã trừ), một bài bị từ chối (phí đã nhả)
        long userId = insertUser(0);
        insertItem(userId, "SWAP", "APPROVED", "CHARGED", 0, null);
        insertItem(userId, "SWAP", "REJECTED", "RELEASED", 0, null);
        String token = jwtService.issue(userId, false);

        // when + then
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getHeldFees_whenOtherUserCalls_doesNotSeeMyItems() throws Exception {
        // given: user A đang giữ phí, user B không có gì
        long ownerId = insertUser(FEE);
        insertItem(ownerId, "SWAP", "PENDING_REVIEW", "HELD", FEE, "POST");
        long otherUserId = insertUser(0);
        String otherToken = jwtService.issue(otherUserId, false);

        // when + then
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getHeldFees_withoutToken_returnsUnauthorized() throws Exception {
        // given: không gửi token

        // when + then
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private long insertUser(int heldCredit) {
        // Đầu số 0997 khác các IT khác (0998, 0999) để không đụng unique index phone.
        int sequence = PHONE_SEQUENCE.incrementAndGet();
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'not-a-real-hash', 'HeldFees IT', ?, 'ACTIVE', ?, ?) RETURNING id",
                Long.class,
                "held-fees-it-" + sequence + "-" + System.nanoTime() + "@test.local",
                String.format("0997%06d", sequence),
                heldCredit,
                heldCredit);
        createdUserIds.add(userId);
        return userId;
    }

    // desired_item bắt buộc với SWAP (ck_items_swap_desired), để null với GIVE.
    private long insertItem(
            long donorId, String offerType, String status, String feeState, int pendingFee, String feeType) {
        String desiredItem = "SWAP".equals(offerType) ? "Món muốn đổi" : null;
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, desired_item,"
                        + " condition, status, fee_state, pending_fee, pending_fee_type)"
                        + " VALUES (?, (SELECT id FROM item_categories ORDER BY id LIMIT 1),"
                        + " (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1),"
                        + " ?, 'HeldFees IT item', 'Mô tả', ?, 'NEW', ?, ?, ?, ?) RETURNING id",
                Long.class,
                donorId,
                offerType,
                desiredItem,
                status,
                feeState,
                pendingFee,
                feeType);
    }
}
