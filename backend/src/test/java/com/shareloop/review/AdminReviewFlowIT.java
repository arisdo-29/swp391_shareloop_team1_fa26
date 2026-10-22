package com.shareloop.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Luồng Admin duyệt bài qua HTTP thật trên PostgreSQL: xem chi tiết bài ở mọi trạng thái, và các điều kiện duyệt / từ
 * chối. Không @Transactional ở lớp test để hai request đồng thời commit thật; dữ liệu tự tạo được dọn trong cleanUp().
 */
@AutoConfigureMockMvc
class AdminReviewFlowIT extends IntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final int POST_FEE = 10;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ConfigService configService;

    private final List<Long> createdUserIds = new ArrayList<>();
    private long adminId;
    private long donorId;
    private String adminToken;

    @BeforeEach
    void createUsers() {
        adminId = createUser(0);
        donorId = createUser(100);
        adminToken = jwtService.issue(adminId, true);
    }

    @AfterEach
    void cleanUp() {
        for (long userId : createdUserIds) {
            jdbc.sql("DELETE FROM activity_logs WHERE actor_id = :id OR (target_type = 'ITEM' AND target_id IN "
                            + "(SELECT id FROM items WHERE donor_id = :id))")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM notifications WHERE user_id = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM credit_ledger WHERE user_id = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM item_media_mappings WHERE item_id IN (SELECT id FROM items WHERE donor_id = :id)")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM item_attribute_mappings WHERE item_id IN (SELECT id FROM items WHERE donor_id = :id)")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM items WHERE donor_id = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM media_files WHERE created_by = :id")
                    .param("id", userId)
                    .update();
        }
        jdbc.sql("DELETE FROM item_attributes WHERE name LIKE 'Review IT %'").update();
        for (long userId : createdUserIds) {
            jdbc.sql("DELETE FROM users WHERE id = :id").param("id", userId).update();
        }
        createdUserIds.clear();
    }

    // ---------- GET /api/v1/admin/reviews/{itemId} ----------

    @Test
    void getDetail_whenAdminViewsPendingItem_returnsFullDetail() throws Exception {
        // given
        long itemId = insertItem("SWAP", "PENDING_REVIEW", "HELD", POST_FEE);
        long photoId = attachMedia(itemId, "PHOTO");
        long defectId = attachMedia(itemId, "DEFECT");
        long attributeId = insertAttribute(itemId, "Review IT Kích cỡ", "L");

        // when / then
        mvc.perform(get("/api/v1/admin/reviews/{id}", itemId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId))
                .andExpect(jsonPath("$.title").value("Bài chờ duyệt"))
                .andExpect(jsonPath("$.offerType").value("SWAP"))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.images[0]").value("/api/v1/media/" + photoId))
                .andExpect(jsonPath("$.defectImages[0]").value("/api/v1/media/" + defectId))
                .andExpect(jsonPath("$.categoryName").isNotEmpty())
                .andExpect(jsonPath("$.areaName").isNotEmpty())
                .andExpect(jsonPath("$.donorId").value(donorId))
                .andExpect(jsonPath("$.donorName").value("Review IT"))
                .andExpect(jsonPath("$.donorTrustStars").isNumber())
                .andExpect(jsonPath("$.attributes", hasSize(1)))
                .andExpect(jsonPath("$.attributes[0].attributeId").value(attributeId))
                .andExpect(jsonPath("$.attributes[0].name").value("Review IT Kích cỡ"))
                .andExpect(jsonPath("$.attributes[0].value").value("L"))
                .andExpect(jsonPath("$.authStatus").value("NONE"))
                .andExpect(jsonPath("$.checklist").isNotEmpty())
                .andExpect(jsonPath("$.checklist[0].statement").isNotEmpty());
    }

    @Test
    void getDetail_whenItemHasNoAttributes_returnsEmptyAttributes() throws Exception {
        // given
        long itemId = insertItem("GIVE", "PENDING_REVIEW", "HELD", 0);

        // when / then
        mvc.perform(get("/api/v1/admin/reviews/{id}", itemId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes", hasSize(0)));
    }

    @Test
    void getDetail_whenItemRejectedOrHidden_stillReturnsItForAdmin() throws Exception {
        // given
        long rejectedId = insertItem("GIVE", "REJECTED", "RELEASED", 0);
        long hiddenId = insertItem("GIVE", "APPROVED", "CHARGED", 0);
        jdbc.sql("UPDATE items SET is_active = FALSE WHERE id = :id")
                .param("id", hiddenId)
                .update();

        // when / then
        mvc.perform(get("/api/v1/admin/reviews/{id}", rejectedId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/api/v1/admin/reviews/{id}", hiddenId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void getDetail_whenNormalUser_returnsForbidden() throws Exception {
        // given
        long itemId = insertItem("GIVE", "PENDING_REVIEW", "HELD", 0);
        String userToken = jwtService.issue(donorId, false);

        // when / then
        mvc.perform(get("/api/v1/admin/reviews/{id}", itemId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDetail_whenNotLoggedIn_returnsUnauthorized() throws Exception {
        // given
        long itemId = insertItem("GIVE", "PENDING_REVIEW", "HELD", 0);

        // when / then
        mvc.perform(get("/api/v1/admin/reviews/{id}", itemId)).andExpect(status().isUnauthorized());
    }

    @Test
    void getDetail_whenItemMissingOrDeleted_returnsNotFound() throws Exception {
        // given
        long deletedId = insertItem("GIVE", "PENDING_REVIEW", "HELD", 0);
        jdbc.sql("UPDATE items SET is_deleted = TRUE WHERE id = :id")
                .param("id", deletedId)
                .update();

        // when / then
        mvc.perform(get("/api/v1/admin/reviews/{id}", 999_999_999L).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_ITEM_NOT_FOUND"));
        mvc.perform(get("/api/v1/admin/reviews/{id}", deletedId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_ITEM_NOT_FOUND"));
    }

    @Test
    void pendingItemImages_areUploadedAsPublicAndReadableWithoutToken() throws Exception {
        // given: ảnh PHOTO/DEFECT của bài chờ duyệt, tạo như luồng upload thật (visibility mặc định)
        long itemId = insertItem("SWAP", "PENDING_REVIEW", "HELD", POST_FEE);
        long photoId = attachMedia(itemId, "PHOTO");

        // when
        int statusCode = mvc.perform(get("/api/v1/media/{id}", photoId))
                .andReturn()
                .getResponse()
                .getStatus();

        // then: không bị chặn bởi đăng nhập hay quyền (tệp vật lý không có trong test nên có thể là lỗi tệp thiếu)
        assertThat(statusCode).isNotIn(401, 403);
        assertThat(jdbc.sql("SELECT visibility FROM media_files WHERE id = :id")
                        .param("id", photoId)
                        .query(String.class)
                        .single())
                .isEqualTo("PUBLIC");
    }

    // ---------- POST approve / reject ----------

    @Test
    void approve_whenChecklistItemMissing_returnsBadRequestAndKeepsWallet() throws Exception {
        // given
        long itemId = insertItem("SWAP", "PENDING_REVIEW", "HELD", POST_FEE);
        List<Long> ids = checklistIds();

        // when / then: bỏ sót mục cuối
        approve(itemId, answers(ids.subList(0, ids.size() - 1), true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_CHECKLIST_INCOMPLETE"));
        assertThat(statusOf(itemId)).isEqualTo("PENDING_REVIEW");
        assertThat(balanceOf(donorId)).isEqualTo(100);
    }

    @Test
    void approve_whenAnswerNotPassed_returnsBadRequest() throws Exception {
        // given
        long itemId = insertItem("SWAP", "PENDING_REVIEW", "HELD", POST_FEE);

        // when / then
        approve(itemId, answers(checklistIds(), false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_CHECKLIST_INCOMPLETE"));
        assertThat(statusOf(itemId)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void approve_whenChecklistOutdated_returnsConflict() throws Exception {
        // given: có id không thuộc checklist đang dùng
        long itemId = insertItem("SWAP", "PENDING_REVIEW", "HELD", POST_FEE);
        List<Long> ids = new ArrayList<>(checklistIds());
        ids.add(999_999_999L);

        // when / then
        approve(itemId, answers(ids, true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_CHECKLIST_OUTDATED"));
    }

    @Test
    void approve_whenGiveItem_succeedsWithoutChargingCredit() throws Exception {
        // given
        long itemId = insertItem("GIVE", "PENDING_REVIEW", "HELD", 0);

        // when / then
        approve(itemId, answers(checklistIds(), true)).andExpect(status().isNoContent());
        assertThat(statusOf(itemId)).isEqualTo("APPROVED");
        assertThat(balanceOf(donorId)).isEqualTo(100);
        assertThat(ledgerCount(donorId)).isZero();
    }

    @Test
    void approve_whenSwapItem_chargesHeldFeeOnce() throws Exception {
        // given
        long itemId = insertItem("SWAP", "PENDING_REVIEW", "HELD", POST_FEE);

        // when / then
        approve(itemId, answers(checklistIds(), true)).andExpect(status().isNoContent());
        assertThat(statusOf(itemId)).isEqualTo("APPROVED");
        assertThat(balanceOf(donorId)).isEqualTo(100 - POST_FEE);
        assertThat(ledgerCount(donorId)).isEqualTo(1);
    }

    @Test
    void approve_whenItemAlreadyApproved_returnsStatusConflictAndKeepsBalance() throws Exception {
        // given: bài đã được duyệt và trừ phí một lần
        long itemId = insertItem("SWAP", "PENDING_REVIEW", "HELD", POST_FEE);
        approve(itemId, answers(checklistIds(), true)).andExpect(status().isNoContent());
        long balanceAfterFirst = balanceOf(donorId);

        // when / then: duyệt lần hai báo lỗi trạng thái, không phải lỗi phí
        approve(itemId, answers(checklistIds(), true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_INVALID_STATUS_TRANSITION"));
        assertThat(balanceOf(donorId)).isEqualTo(balanceAfterFirst);
        assertThat(ledgerCount(donorId)).isEqualTo(1);
    }

    @Test
    void approve_whenItemAlreadyRejected_returnsStatusConflictAndKeepsBalance() throws Exception {
        // given
        long itemId = insertItem("SWAP", "PENDING_REVIEW", "HELD", POST_FEE);
        reject(itemId, "Ảnh mờ").andExpect(status().isNoContent());
        long balanceAfterReject = balanceOf(donorId);

        // when / then
        approve(itemId, answers(checklistIds(), true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_INVALID_STATUS_TRANSITION"));
        assertThat(balanceOf(donorId)).isEqualTo(balanceAfterReject);
        assertThat(statusOf(itemId)).isEqualTo("REJECTED");
    }

    @Test
    void reject_whenItemAlreadyProcessed_returnsStatusConflictAndKeepsBalance() throws Exception {
        // given: bài đã duyệt (phí đã trừ)
        long itemId = insertItem("SWAP", "PENDING_REVIEW", "HELD", POST_FEE);
        approve(itemId, answers(checklistIds(), true)).andExpect(status().isNoContent());
        long balanceAfterApprove = balanceOf(donorId);

        // when / then: từ chối bài đã duyệt
        reject(itemId, "Nhầm")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_INVALID_STATUS_TRANSITION"));
        // và từ chối lần hai bài đã bị từ chối
        long otherId = insertItem("SWAP", "PENDING_REVIEW", "HELD", POST_FEE);
        reject(otherId, "Ảnh mờ").andExpect(status().isNoContent());
        reject(otherId, "Ảnh mờ")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_INVALID_STATUS_TRANSITION"));
        assertThat(balanceOf(donorId)).isEqualTo(balanceAfterApprove);
    }

    @Test
    void approve_whenItemMissing_returnsNotFound() throws Exception {
        // when / then
        approve(999_999_999L, answers(checklistIds(), true))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_ITEM_NOT_FOUND"));
    }

    @Test
    void approve_whenTwoAdminsApproveAtSameTime_onlyOneSucceedsAndFeeChargedOnce() throws Exception {
        // given
        long itemId = insertItem("SWAP", "PENDING_REVIEW", "HELD", POST_FEE);
        String body = answers(checklistIds(), true);
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // when: hai request cùng bắt đầu
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Callable<Integer> task = () -> {
                startSignal.await();
                return approve(itemId, body).andReturn().getResponse().getStatus();
            };
            results.add(executor.submit(task));
        }
        startSignal.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        executor.shutdown();

        // then: một 204, một 409; phí chỉ trừ một lần
        assertThat(statuses).containsExactlyInAnyOrder(204, 409);
        assertThat(statusOf(itemId)).isEqualTo("APPROVED");
        assertThat(balanceOf(donorId)).isEqualTo(100 - POST_FEE);
        assertThat(ledgerCount(donorId)).isEqualTo(1);
    }

    // ---------- helper ----------

    private org.springframework.test.web.servlet.ResultActions approve(long itemId, String body) throws Exception {
        return mvc.perform(post("/api/v1/admin/reviews/{id}/approve", itemId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":" + body + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions reject(long itemId, String reason) throws Exception {
        return mvc.perform(post("/api/v1/admin/reviews/{id}/reject", itemId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}"));
    }

    private List<Long> checklistIds() {
        return configService.checklistItems().stream().map(item -> item.id()).toList();
    }

    private String answers(List<Long> ids, boolean passed) {
        List<String> parts = new ArrayList<>();
        for (Long id : ids) {
            parts.add("{\"id\":" + id + ",\"passed\":" + passed + "}");
        }
        return "[" + String.join(",", parts) + "]";
    }

    private long createUser(int creditBalance) {
        int sequence = SEQUENCE.incrementAndGet();
        long userId = jdbc.sql(
                        "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit) "
                                + "VALUES (:email, 'test-only', 'Review IT', :phone, 'ACTIVE', :balance, 0) RETURNING id")
                .param("email", "review-flow-" + sequence + "-" + System.nanoTime() + "@test.local")
                .param("phone", String.format("0975%06d", sequence))
                .param("balance", creditBalance)
                .query(Long.class)
                .single();
        createdUserIds.add(userId);
        return userId;
    }

    // Dựng bài thẳng bằng SQL; bài đang giữ phí thì ví của chủ bài cũng giữ đúng khoản đó (held_credit).
    private long insertItem(String offerType, String itemStatus, String feeState, int pendingFee) {
        long itemId = jdbc.sql("INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, "
                        + "condition, desired_item, status, fee_state, pending_fee, pending_fee_type) "
                        + "VALUES (:donorId, (SELECT id FROM item_categories WHERE is_active AND NOT is_deleted "
                        + "ORDER BY id LIMIT 1), (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1), "
                        + ":offerType, 'Bài chờ duyệt', 'Mô tả bài', 'GOOD', CASE WHEN :offerType = 'SWAP' THEN 'Sách' END, :status, :feeState, :pendingFee, 'POST') "
                        + "RETURNING id")
                .param("donorId", donorId)
                .param("offerType", offerType)
                .param("status", itemStatus)
                .param("feeState", feeState)
                .param("pendingFee", pendingFee)
                .query(Long.class)
                .single();
        if ("HELD".equals(feeState) && pendingFee > 0) {
            jdbc.sql("UPDATE users SET held_credit = held_credit + :fee WHERE id = :id")
                    .param("fee", pendingFee)
                    .param("id", donorId)
                    .update();
        }
        return itemId;
    }

    private long attachMedia(long itemId, String role) {
        long mediaId = jdbc.sql("INSERT INTO media_files (storage_key, mime_type, size_bytes, created_by, status) "
                        + "VALUES (gen_random_uuid()::text, 'image/png', 10, :userId, 'ATTACHED') RETURNING id")
                .param("userId", donorId)
                .query(Long.class)
                .single();
        jdbc.sql("INSERT INTO item_media_mappings (item_id, media_id, role, sort_order) "
                        + "VALUES (:itemId, :mediaId, :role, 0)")
                .param("itemId", itemId)
                .param("mediaId", mediaId)
                .param("role", role)
                .update();
        return mediaId;
    }

    private long insertAttribute(long itemId, String name, String value) {
        long attributeId = jdbc.sql("INSERT INTO item_attributes (category_id, name, data_type) "
                        + "VALUES ((SELECT category_id FROM items WHERE id = :itemId), :name, 'TEXT') RETURNING id")
                .param("itemId", itemId)
                .param("name", name)
                .query(Long.class)
                .single();
        jdbc.sql("INSERT INTO item_attribute_mappings (item_id, attribute_id, value) "
                        + "VALUES (:itemId, :attributeId, :value)")
                .param("itemId", itemId)
                .param("attributeId", attributeId)
                .param("value", value)
                .update();
        return attributeId;
    }

    private String statusOf(long itemId) {
        return jdbc.sql("SELECT status FROM items WHERE id = :id")
                .param("id", itemId)
                .query(String.class)
                .single();
    }

    private long balanceOf(long userId) {
        return jdbc.sql("SELECT credit_balance FROM users WHERE id = :id")
                .param("id", userId)
                .query(Long.class)
                .single();
    }

    private long ledgerCount(long userId) {
        return jdbc.sql("SELECT count(*) FROM credit_ledger WHERE user_id = :id")
                .param("id", userId)
                .query(Long.class)
                .single();
    }
}
