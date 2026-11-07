package com.shareloop.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.chat.dto.ProposeOfferChangeRequest;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.service.ItemService;
import com.shareloop.request.dto.CancelTransactionRequest;
import com.shareloop.request.dto.ConfirmLogisticsRequest;
import com.shareloop.request.dto.HandoverConfirmRequest;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration test đề nghị đưa món vào đổi trong chat: giao dịch Cho đã chọn người chuyển thành Trao đổi khi chủ bài
 * đồng ý (tình huống 2, CD-02, CD-06, CD-07), chạy trên PostgreSQL thật (Testcontainers).
 */
@AutoConfigureMockMvc
class OfferChangeIT extends IntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final int DONOR_BALANCE = 20;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ConfigService configService;

    // Spy để giả lỗi giữ chỗ món SAU khi đã trừ tiền, kiểm tra cả transaction hoàn tác; các test khác chạy hàm thật.
    @MockitoSpyBean
    ItemService itemService;

    private final List<Long> createdRequestIds = new ArrayList<>();
    private final List<Long> createdItemIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    // Dữ liệu chung của mỗi test: giao dịch Cho đã chọn người, người nhận có một món APPROVED để đề nghị.
    private long donorId;
    private long receiverId;
    private long mainItemId;
    private long offeredItemId;
    private long requestId;

    @AfterEach
    void cleanUp() {
        for (Long id : createdRequestIds) {
            jdbc.update("DELETE FROM messages WHERE request_id = ?", id);
            jdbc.update("DELETE FROM requests WHERE id = ?", id);
        }
        createdRequestIds.clear();
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM credit_ledger WHERE user_id = ?", userId);
        }
        for (Long itemId : createdItemIds) {
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        createdItemIds.clear();
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM activity_logs WHERE actor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUserIds.clear();
    }

    @Test
    @DisplayName("Người nhận đề nghị: có tin OFFER_CHANGE PENDING kèm thẻ món; đề nghị lần 2 thì tin cũ thành DECLINED")
    void propose_twice_declinesPreviousOffer() throws Exception {
        // given
        givenReservedGiveTransaction(DONOR_BALANCE);
        long secondOfferedItemId = insertItem(receiverId, "GIVE", "APPROVED");

        // when
        MvcResult firstOffer = proposeViaApi(receiverId, offeredItemId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("OFFER_CHANGE"))
                .andExpect(jsonPath("$.offerStatus").value("PENDING"))
                .andExpect(jsonPath("$.itemCard.id").value(offeredItemId))
                .andReturn();
        long firstOfferId = readId(firstOffer.getResponse().getContentAsString());
        proposeViaApi(receiverId, secondOfferedItemId).andExpect(status().isCreated());

        // then
        assertThat(queryOfferStatus(firstOfferId)).isEqualTo("DECLINED");
        assertThat(countPendingOffers()).isEqualTo(1);
        assertThat(countNotifications(donorId, "OFFER_CHANGE_PROPOSED")).isEqualTo(2);
    }

    @Test
    @DisplayName("Chủ bài đồng ý: trừ POST_FEE, bài và giao dịch thành SWAP, giữ chỗ món, huỷ yêu cầu khác, ghim 2 món")
    void accept_convertsTransactionToSwap() throws Exception {
        // given: món đề nghị đang được dùng trong một yêu cầu PENDING khác ở bài của người khác
        givenReservedGiveTransaction(DONOR_BALANCE);
        long otherDonorId = insertUser(0);
        long otherItemId = insertItem(otherDonorId, "SWAP", "APPROVED");
        long otherPendingRequestId = insertRequest(otherItemId, receiverId, "SWAP", "PENDING", offeredItemId);
        long offerId = proposeAndReadId();

        // when
        acceptViaApi(donorId, offerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SWAP"))
                .andExpect(jsonPath("$.offeredItemId").value(offeredItemId))
                .andExpect(jsonPath("$.itemOfferType").value("SWAP"));

        // then
        assertThat(queryCreditBalance(donorId)).isEqualTo(DONOR_BALANCE - postFee());
        assertThat(countPostFeeLedger(donorId)).isEqualTo(1);
        assertThat(queryItemOfferType(mainItemId)).isEqualTo("SWAP");
        assertThat(queryRequestType()).isEqualTo("SWAP");
        assertThat(queryItemStatus(offeredItemId)).isEqualTo("RESERVED");
        assertThat(queryRequestStatus(otherPendingRequestId)).isEqualTo("CANCELLED");
        assertThat(queryOfferStatus(offerId)).isEqualTo("ACCEPTED");
        assertThat(countSystemMessages()).isEqualTo(1);
        assertThat(countNotifications(receiverId, "OFFER_CHANGE_ACCEPTED")).isEqualTo(1);
        mockMvc.perform(get("/api/v1/requests/{requestId}/pinned-items", requestId)
                        .header("Authorization", token(receiverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].id").value(offeredItemId));
    }

    @Test
    @DisplayName("Chủ bài thiếu Credit: 402, không đổi gì, đề nghị vẫn PENDING, số dư không đổi")
    void accept_whenDonorLacksCredit_returns402AndChangesNothing() throws Exception {
        // given
        givenReservedGiveTransaction(postFee() - 1);
        long offerId = proposeAndReadId();

        // when
        acceptViaApi(donorId, offerId)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("WALLET_INSUFFICIENT_BALANCE"));

        // then
        assertThat(queryOfferStatus(offerId)).isEqualTo("PENDING");
        assertThat(queryCreditBalance(donorId)).isEqualTo(postFee() - 1);
        assertThat(queryItemOfferType(mainItemId)).isEqualTo("GIVE");
        assertThat(queryRequestType()).isEqualTo("GIVE");
        assertThat(queryRequestOfferedItemId()).isNull();
        assertThat(queryItemStatus(offeredItemId)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("Món đề nghị đã bị giữ ở giao dịch khác lúc đồng ý: 409, không trừ tiền, đề nghị vẫn PENDING")
    void accept_whenOfferedItemReservedElsewhere_returns409AndKeepsCredit() throws Exception {
        // given
        givenReservedGiveTransaction(DONOR_BALANCE);
        long offerId = proposeAndReadId();
        jdbc.update("UPDATE items SET status = 'RESERVED' WHERE id = ?", offeredItemId);

        // when
        acceptViaApi(donorId, offerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ITEM_NOT_AVAILABLE"));

        // then
        assertThat(queryCreditBalance(donorId)).isEqualTo(DONOR_BALANCE);
        assertThat(queryOfferStatus(offerId)).isEqualTo("PENDING");
        assertThat(queryItemOfferType(mainItemId)).isEqualTo("GIVE");
    }

    @Test
    @DisplayName("Giữ chỗ món lỗi SAU khi đã trừ phí: cả transaction hoàn tác, kể cả tiền và loại bài")
    void accept_whenReserveFailsAfterCharging_rollsBackEverything() throws Exception {
        // given: giả giao dịch khác vừa giữ món ngay sau lúc kiểm tra, nên reserve ném lỗi khi đã trừ tiền
        givenReservedGiveTransaction(DONOR_BALANCE);
        long offerId = proposeAndReadId();
        doThrow(new BusinessException(ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION))
                .when(itemService)
                .reserve(offeredItemId);

        // when
        acceptViaApi(donorId, offerId).andExpect(status().is4xxClientError());

        // then
        assertThat(queryCreditBalance(donorId)).isEqualTo(DONOR_BALANCE);
        assertThat(countPostFeeLedger(donorId)).isZero();
        assertThat(queryItemOfferType(mainItemId)).isEqualTo("GIVE");
        assertThat(queryRequestType()).isEqualTo("GIVE");
        assertThat(queryOfferStatus(offerId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Chủ bài từ chối: đề nghị DECLINED, có tin SYSTEM, báo người nhận, không trừ tiền")
    void decline_marksDeclinedWithoutCharging() throws Exception {
        // given
        givenReservedGiveTransaction(DONOR_BALANCE);
        long offerId = proposeAndReadId();

        // when
        mockMvc.perform(post("/api/v1/requests/{requestId}/offer-changes/{messageId}/decline", requestId, offerId)
                        .header("Authorization", token(donorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerStatus").value("DECLINED"));

        // then
        assertThat(queryCreditBalance(donorId)).isEqualTo(DONOR_BALANCE);
        assertThat(queryRequestType()).isEqualTo("GIVE");
        assertThat(countSystemMessages()).isEqualTo(1);
        assertThat(countNotifications(receiverId, "OFFER_CHANGE_DECLINED")).isEqualTo(1);
    }

    @Test
    @DisplayName("Sai người: chủ bài gửi đề nghị, người nhận tự đồng ý, người ngoài gửi đề nghị đều bị 403")
    void wrongActors_areForbidden() throws Exception {
        // given
        givenReservedGiveTransaction(DONOR_BALANCE);
        long strangerId = insertUser(0);
        long offerId = proposeAndReadId();

        // when & then
        proposeViaApi(donorId, offeredItemId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_FORBIDDEN"));
        acceptViaApi(receiverId, offerId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_FORBIDDEN"));
        proposeViaApi(strangerId, offeredItemId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_FORBIDDEN"));
        assertThat(queryOfferStatus(offerId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Giao dịch đã là SWAP hoặc đã LOGISTICS_CONFIRMED thì gửi đề nghị bị 409")
    void propose_whenRequestNotGiveOrAlreadyConfirmed_returns409() throws Exception {
        // given
        givenReservedGiveTransaction(DONOR_BALANCE);

        // when & then: giao dịch đã là Trao đổi
        jdbc.update("UPDATE requests SET type = 'SWAP' WHERE id = ?", requestId);
        proposeViaApi(receiverId, offeredItemId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_OFFER_CHANGE_NOT_ALLOWED"));

        // when & then: giao dịch Cho nhưng đã chốt lịch
        jdbc.update("UPDATE requests SET type = 'GIVE', status = 'LOGISTICS_CONFIRMED' WHERE id = ?", requestId);
        proposeViaApi(receiverId, offeredItemId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_OFFER_CHANGE_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Giao dịch sang LOGISTICS_CONFIRMED thì đề nghị PENDING tự thành DECLINED")
    void confirmLogistics_declinesPendingOffer() throws Exception {
        // given
        givenReservedGiveTransaction(DONOR_BALANCE);
        long offerId = proposeAndReadId();

        // when
        scheduleAndConfirmLogistics();

        // then
        assertThat(queryRequestStatus(requestId)).isEqualTo("LOGISTICS_CONFIRMED");
        assertThat(queryOfferStatus(offerId)).isEqualTo("DECLINED");
        assertThat(countSystemMessages()).isEqualTo(1);
    }

    @Test
    @DisplayName("Giao dịch bị huỷ thì đề nghị PENDING tự thành DECLINED")
    void cancelTransaction_declinesPendingOffer() throws Exception {
        // given
        givenReservedGiveTransaction(DONOR_BALANCE);
        long offerId = proposeAndReadId();

        // when
        mockMvc.perform(post("/api/v1/requests/{id}/cancel-transaction", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelTransactionRequest("Bận đột xuất"))))
                .andExpect(status().isOk());

        // then
        assertThat(queryRequestStatus(requestId)).isEqualTo("CANCELLED");
        assertThat(queryOfferStatus(offerId)).isEqualTo("DECLINED");
    }

    @Test
    @DisplayName("Hoàn tất giao dịch sau khi chuyển: điểm uy tín tính theo SWAP cho cả hai bên (CD-07)")
    void completeAfterConversion_awardsSwapPoints() throws Exception {
        // given
        givenReservedGiveTransaction(DONOR_BALANCE);
        long offerId = proposeAndReadId();
        acceptViaApi(donorId, offerId).andExpect(status().isOk());
        scheduleAndConfirmLogistics();

        // when
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", token(donorId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HandoverConfirmRequest(true, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // then
        int swapPoints = configService.getInt(ConfigKey.REPUTATION_SWAP_EACH);
        assertThat(queryTotalPoints(donorId)).isEqualTo(swapPoints);
        assertThat(queryTotalPoints(receiverId)).isEqualTo(swapPoints);
        assertThat(queryItemStatus(mainItemId)).isEqualTo("TRADED");
        assertThat(queryItemStatus(offeredItemId)).isEqualTo("TRADED");
    }

    // ---------- Tiện ích hỗ trợ test ----------

    private void givenReservedGiveTransaction(int donorBalance) {
        donorId = insertUser(donorBalance);
        receiverId = insertUser(0);
        mainItemId = insertItem(donorId, "GIVE", "RESERVED");
        offeredItemId = insertItem(receiverId, "GIVE", "APPROVED");
        requestId = insertRequest(mainItemId, receiverId, "GIVE", "RESERVED", null);
    }

    private int postFee() {
        return configService.getInt(ConfigKey.POST_FEE);
    }

    private ResultActions proposeViaApi(long userId, long itemId) throws Exception {
        return mockMvc.perform(post("/api/v1/requests/{requestId}/offer-changes", requestId)
                .header("Authorization", token(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ProposeOfferChangeRequest(itemId, "Mình đổi món này nhé"))));
    }

    private long proposeAndReadId() throws Exception {
        MvcResult result = proposeViaApi(receiverId, offeredItemId)
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private ResultActions acceptViaApi(long userId, long offerId) throws Exception {
        return mockMvc.perform(post("/api/v1/requests/{requestId}/offer-changes/{messageId}/accept", requestId, offerId)
                .header("Authorization", token(userId)));
    }

    private void scheduleAndConfirmLogistics() throws Exception {
        Instant meetingTime = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        mockMvc.perform(post("/api/v1/requests/{id}/schedule", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ScheduleRequest("IN_PERSON", "Cổng trường", meetingTime, false))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmLogisticsRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGISTICS_CONFIRMED"));
    }

    private long readId(String json) {
        return objectMapper.readTree(json).get("id").asLong();
    }

    private String token(long userId) {
        return "Bearer " + jwtService.issue(userId, false);
    }

    private long insertUser(int creditBalance) {
        int seq = SEQ.incrementAndGet();
        String email = "offer-change-" + seq + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0998%06d", seq);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'hash', 'Offer Change User', ?, 'ACTIVE', ?, 0) RETURNING id",
                Long.class,
                email,
                phone,
                creditBalance);
        createdUserIds.add(userId);
        return userId;
    }

    private long insertItem(long ownerId, String offerType, String status) {
        long categoryId = jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
        long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        String desiredItem = "SWAP".equals(offerType) ? "Muốn đổi lấy đồ dùng học tập" : null;
        Long itemId = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, desired_item,"
                        + " condition, status, fee_state, pending_fee, pending_fee_type)"
                        + " VALUES (?, ?, ?, ?, 'Offer Change Item', 'Mô tả', ?, 'GOOD', ?, 'CHARGED', 0, NULL)"
                        + " RETURNING id",
                Long.class,
                ownerId,
                categoryId,
                areaId,
                offerType,
                desiredItem,
                status);
        createdItemIds.add(itemId);
        return itemId;
    }

    private long insertRequest(long itemId, long requestReceiverId, String type, String status, Long offeredId) {
        Long id = jdbc.queryForObject(
                "INSERT INTO requests (item_id, receiver_id, offered_item_id, type, status, reserved_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                itemId,
                requestReceiverId,
                offeredId,
                type,
                status,
                "PENDING".equals(status) ? null : Timestamp.from(Instant.now()));
        createdRequestIds.add(id);
        return id;
    }

    private String queryOfferStatus(long messageId) {
        return jdbc.queryForObject("SELECT offer_status FROM messages WHERE id = ?", String.class, messageId);
    }

    private int countPendingOffers() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE request_id = ? AND kind = 'OFFER_CHANGE'"
                        + " AND offer_status = 'PENDING'",
                Integer.class,
                requestId);
    }

    private int countSystemMessages() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE request_id = ? AND kind = 'SYSTEM'", Integer.class, requestId);
    }

    private int countNotifications(long userId, String type) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = ?", Integer.class, userId, type);
    }

    private int countPostFeeLedger(long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM credit_ledger WHERE user_id = ? AND type = 'POST_FEE'", Integer.class, userId);
    }

    private String queryRequestType() {
        return jdbc.queryForObject("SELECT type FROM requests WHERE id = ?", String.class, requestId);
    }

    private Long queryRequestOfferedItemId() {
        return jdbc.queryForObject("SELECT offered_item_id FROM requests WHERE id = ?", Long.class, requestId);
    }

    private String queryRequestStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, id);
    }

    private String queryItemOfferType(long itemId) {
        return jdbc.queryForObject("SELECT offer_type FROM items WHERE id = ?", String.class, itemId);
    }

    private String queryItemStatus(long itemId) {
        return jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId);
    }

    private int queryCreditBalance(long userId) {
        return jdbc.queryForObject("SELECT credit_balance FROM users WHERE id = ?", Integer.class, userId);
    }

    private int queryTotalPoints(long userId) {
        return jdbc.queryForObject("SELECT total_points FROM users WHERE id = ?", Integer.class, userId);
    }
}
