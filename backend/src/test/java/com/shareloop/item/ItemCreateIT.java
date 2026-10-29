package com.shareloop.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.review.dto.ChecklistAnswer;
import com.shareloop.review.service.ReviewService;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ItemCreateIT extends IntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ReviewService reviewService;

    @Autowired
    ConfigService configService;

    private final List<Long> users = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (long userId : users) {
            // Duyệt bài ghi activity_logs (REVIEW_APPROVE) trỏ tới bài; xoá cho sạch dữ liệu test.
            jdbc.sql("DELETE FROM activity_logs WHERE target_type = 'ITEM' "
                            + "AND target_id IN (SELECT id FROM items WHERE donor_id = :id)")
                    .param("id", userId)
                    .update();
            // Từ chối bài gửi thông báo POST_REJECTED cho chủ bài; phải xoá trước khi xoá user.
            jdbc.sql("DELETE FROM notifications WHERE user_id = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM item_media_mappings WHERE item_id IN (SELECT id FROM items WHERE donor_id = :id)")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM credit_ledger WHERE user_id = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM items WHERE donor_id = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM media_files WHERE created_by = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM users WHERE id = :id").param("id", userId).update();
        }
    }

    @Test
    void cleanPostHoldsFeeAndAttachesImages() throws Exception {
        long owner = user(10);
        long[] images = media(owner);

        mvc.perform(create(owner, body("SWAP", "Sách học", "Sách còn tốt", images)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        long itemId = itemId(owner);
        assertThat(jdbc.sql("SELECT fee_state FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("HELD");
        assertThat(jdbc.sql("SELECT pending_fee FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(5);
        assertThat(held(owner)).isEqualTo(5);
        assertThat(jdbc.sql("SELECT count(*) FROM item_media_mappings WHERE item_id = :id")
                        .param("id", itemId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(3);
    }

    @Test
    void giveLimitCountsOnlyVisibleGiveItemsAndIgnoresSwapItems() throws Exception {
        long owner = user(10);
        long[] images = media(owner);
        // 5 bài Cho tặng được tính (chờ duyệt hoặc đã duyệt đang hiển thị), các bài còn lại không tính
        item(owner, "GIVE", "PENDING_REVIEW", true, false);
        item(owner, "GIVE", "PENDING_REVIEW", true, false);
        item(owner, "GIVE", "PENDING_REVIEW", true, false);
        item(owner, "GIVE", "APPROVED", true, false);
        long approved = item(owner, "GIVE", "APPROVED", true, false);
        item(owner, "GIVE", "APPROVED", false, false);
        item(owner, "GIVE", "REJECTED", true, false);
        item(owner, "GIVE", "APPROVED", true, true);
        item(owner, "SWAP", "PENDING_REVIEW", true, false);

        mvc.perform(create(owner, body("Sách học", "Sách còn tốt", images)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_GIVE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.details.limit").value(5))
                .andExpect(jsonPath("$.details.activeCount").value(5));
        assertThat(held(owner)).isZero();

        jdbc.sql("UPDATE items SET is_active = false WHERE id = :id")
                .param("id", approved)
                .update();
        mvc.perform(create(owner, body("Sách học", "Sách còn tốt", images))).andExpect(status().isCreated());
    }

    @Test
    void sixthSwapUsesOverQuotaFeeAndIgnoresGiveItems() throws Exception {
        long owner = user(20);
        long[] images = media(owner);
        // Hạng Đồng có 5 bài Trao đổi phí thường; bài Cho không chiếm ngưỡng này.
        for (int index = 0; index < 5; index++) {
            item(owner, "SWAP", "PENDING_REVIEW", true, false);
            item(owner, "GIVE", "PENDING_REVIEW", true, false);
        }

        mvc.perform(create(owner, body("SWAP", "Sách học", "Sách còn tốt", images)))
                .andExpect(status().isCreated());
        assertThat(pendingFeeOf(itemId(owner))).isEqualTo(10);
        assertThat(held(owner)).isEqualTo(10);
    }

    @Test
    void blockedPostReturnsReasonWithoutHoldingFeeOrAttachingImages() throws Exception {
        long owner = user(10);
        long[] images = media(owner);

        mvc.perform(create(owner, body("Cần sa", "Sách còn tốt", images)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ITEM_MODERATION_BLOCKED"))
                .andExpect(jsonPath("$.field").value("content"))
                .andExpect(jsonPath("$.details.reasonCode").value("LEXICON_BLOCK"));

        assertNoItemOrHold(owner, images);
    }

    @Test
    void reviewPostIsFlaggedForAdmin() throws Exception {
        long owner = user(10);
        long[] images = media(owner);

        mvc.perform(create(owner, body("Sách Zalo", "Sách còn tốt", images))).andExpect(status().isCreated());

        long itemId = itemId(owner);
        assertThat(jdbc.sql("SELECT ai_result ->> 'flag' FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("REVIEW");
        assertThat(jdbc.sql("SELECT ai_result ->> 'reason' FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("LEXICON_REVIEW");
        assertThat(jdbc.sql("SELECT ai_result -> 'matchedTerms' ->> 0 FROM items WHERE id = :id")
                        .param("id", itemId(owner))
                        .query(String.class)
                        .single())
                .isEqualTo("zalo");
    }

    @Test
    void insufficientCreditRollsBackItemAndLeavesImagesUploaded() throws Exception {
        // given: tài khoản 0 Credit đăng bài Trao đổi (loại bài mất phí đăng)
        long owner = user(0);
        long[] images = media(owner);

        // when, then
        mvc.perform(create(owner, body("SWAP", "Sách học", "Sách còn tốt", images)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("WALLET_INSUFFICIENT_BALANCE"));

        assertNoItemOrHold(owner, images);
    }

    @Test
    void createGive_whenZeroCredit_succeedsWithZeroHeldFeeAndWalletUnchanged() throws Exception {
        // given: bài Cho tặng miễn phí đăng; ví không có Credit nào
        long owner = user(0);
        long[] images = media(owner);

        // when
        mvc.perform(create(owner, body("GIVE", "Sách học", "Sách còn tốt", images)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        // then: vẫn HELD với pending_fee = 0 để lúc duyệt trừ phí (0) được; ví không đổi
        long itemId = itemId(owner);
        assertThat(feeStateOf(itemId)).isEqualTo("HELD");
        assertThat(pendingFeeOf(itemId)).isZero();
        assertThat(held(owner)).isZero();
        assertThat(balance(owner)).isZero();
    }

    @Test
    void approveGive_whenCreatedWithZeroCredit_setsExpireAtWithoutPostFeeOrLedger() throws Exception {
        // given: tài khoản 0 Credit vừa đăng bài Cho tặng
        long owner = user(0);
        long[] images = media(owner);
        mvc.perform(create(owner, body("GIVE", "Sách học", "Sách còn tốt", images)))
                .andExpect(status().isCreated());
        long itemId = itemId(owner);
        List<ChecklistAnswer> allPassed = allPassedAnswers();

        // when
        reviewService.approve(itemId, allPassed, null);

        // then: bài được duyệt, bắt đầu hiển thị, không mất Credit nào
        assertThat(jdbc.sql("SELECT status FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("APPROVED");
        assertThat(feeStateOf(itemId)).isEqualTo("CHARGED");
        assertThat(jdbc.sql("SELECT post_fee_paid FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(Boolean.class)
                        .single())
                .isFalse();
        assertThat(jdbc.sql("SELECT expire_at IS NOT NULL FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(jdbc.sql("SELECT count(*) FROM credit_ledger WHERE user_id = :owner")
                        .param("owner", owner)
                        .query(Integer.class)
                        .single())
                .isZero();
        assertThat(balance(owner)).isZero();
    }

    @Test
    void rejectGive_whenCreatedWithZeroCredit_leavesWalletUnchangedAndNoLedger() throws Exception {
        // given: tài khoản 0 Credit vừa đăng bài Cho tặng
        long owner = user(0);
        long itemId = createGiveItem(owner);

        // when
        reviewService.reject(itemId, "Ảnh không rõ", null);

        // then: bài bị từ chối, phí (0) được nhả, ví không đổi và không có dòng sổ cái nào
        assertThat(statusOf(itemId)).isEqualTo("REJECTED");
        assertThat(feeStateOf(itemId)).isEqualTo("RELEASED");
        assertThat(balance(owner)).isZero();
        assertThat(held(owner)).isZero();
        assertThat(ledgerCount(owner)).isZero();
    }

    @Test
    void updateItem_whenRejectedGiveAndZeroCredit_resubmitsWithZeroHeldFee() throws Exception {
        // given: bài Cho tặng bị từ chối, chủ bài 0 Credit
        long owner = user(0);
        long itemId = createGiveItem(owner);
        reviewService.reject(itemId, "Ảnh không rõ", null);

        // when: gửi lại bản sửa vẫn là Cho tặng
        mvc.perform(update(owner, itemId, updateBody("GIVE")))
                // then: gửi lại thành công dù ví 0 Credit, chờ duyệt với khoản giữ bằng 0
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        assertThat(feeStateOf(itemId)).isEqualTo("HELD");
        assertThat(pendingFeeOf(itemId)).isZero();
        assertThat(held(owner)).isZero();
        assertThat(balance(owner)).isZero();
        assertThat(ledgerCount(owner)).isZero();
    }

    @Test
    void updateItem_whenSwapChangedToGive_succeeds() throws Exception {
        // given: bài Trao đổi đã được duyệt (đã trả phí đăng)
        long owner = user(10);
        long[] images = media(owner);
        mvc.perform(create(owner, body("SWAP", "Sách học", "Sách còn tốt", images)))
                .andExpect(status().isCreated());
        long itemId = itemId(owner);
        reviewService.approve(itemId, allPassedAnswers(), null);

        // when: sửa sang Cho tặng (chiều SWAP sang GIVE vẫn được phép)
        mvc.perform(update(owner, itemId, updateBody("GIVE")))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        assertThat(offerTypeOf(itemId)).isEqualTo("GIVE");
    }

    @Test
    void mediaFailureAfterFeeHoldRollsBackItemAndWallet() throws Exception {
        long owner = user(10);
        long other = user(0);
        long[] images = media(owner);
        long foreign = media(other)[0];
        images[2] = foreign;

        mvc.perform(create(owner, body("SWAP", "Sách học", "Sách còn tốt", images)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_OWNER"));

        assertNoItemOrHold(owner, images);
    }

    private long user(int balance) {
        long id = jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance) "
                        + "VALUES (:email, 'unused', 'Test', :phone, 'ACTIVE', :balance) RETURNING id")
                .param("email", "item-create-" + UUID.randomUUID() + "@shareloop.local")
                .param(
                        "phone",
                        "09" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100000000))
                .param("balance", balance)
                .query(Long.class)
                .single();
        users.add(id);
        return id;
    }

    private long item(long owner, String offerType, String status, boolean active, boolean deleted) {
        long category = jdbc.sql("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long area = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        return jdbc.sql(
                        "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, "
                                + "desired_item, condition, status, is_active, is_deleted) VALUES (:owner, :category, :area, "
                                + ":offerType, 'Book', 'Description', :desiredItem, 'GOOD', :status, :active, :deleted) RETURNING id")
                .param("owner", owner)
                .param("category", category)
                .param("area", area)
                .param("offerType", offerType)
                .param("desiredItem", "SWAP".equals(offerType) ? "Other book" : null)
                .param("status", status)
                .param("active", active)
                .param("deleted", deleted)
                .query(Long.class)
                .single();
    }

    private long[] media(long owner) {
        long[] ids = new long[3];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = jdbc.sql("INSERT INTO media_files (storage_key, mime_type, size_bytes, created_by) "
                            + "VALUES (gen_random_uuid()::text, 'image/png', 10, :owner) RETURNING id")
                    .param("owner", owner)
                    .query(Long.class)
                    .single();
        }
        return ids;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create(long owner, String body) {
        return post("/api/v1/items")
                .header("Authorization", "Bearer " + jwtService.issue(owner, false))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String body(String title, String description, long[] images) {
        return body("GIVE", title, description, images);
    }

    // Bài SWAP bắt buộc có desiredItem (BR-S02), nên chỉ thêm trường này khi offerType là SWAP.
    private String body(String offerType, String title, String description, long[] images) {
        long category = jdbc.sql(
                        "SELECT id FROM item_categories WHERE is_active AND NOT is_restricted ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long area = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        String desiredItemField = "SWAP".equals(offerType) ? "\"desiredItem\":\"Sách khác\"," : "";
        return """
                {"categoryId":%d,"areaId":%d,"offerType":"%s",%s"title":"%s",\
                "description":"%s","condition":"GOOD",\
                "images":["/api/v1/media/%d","/api/v1/media/%d","/api/v1/media/%d"]}
                """.formatted(
                category, area, offerType, desiredItemField, title, description, images[0], images[1], images[2]);
    }

    // Bản sửa không gửi images: API giữ nguyên ảnh cũ, nên không cần dựng lại ảnh cho bước gửi lại.
    private String updateBody(String offerType) {
        long category = jdbc.sql(
                        "SELECT id FROM item_categories WHERE is_active AND NOT is_restricted ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long area = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        String desiredItemField = "SWAP".equals(offerType) ? "\"desiredItem\":\"Sách khác\"," : "";
        return """
                {"categoryId":%d,"areaId":%d,"offerType":"%s",%s"title":"Sách học bản sửa",\
                "description":"Sách còn tốt, đã bổ sung mô tả","condition":"GOOD"}
                """.formatted(category, area, offerType, desiredItemField);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder update(
            long owner, long itemId, String body) {
        return put("/api/v1/items/{id}", itemId)
                .header("Authorization", "Bearer " + jwtService.issue(owner, false))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private long createGiveItem(long owner) throws Exception {
        long[] images = media(owner);
        mvc.perform(create(owner, body("GIVE", "Sách học", "Sách còn tốt", images)))
                .andExpect(status().isCreated());
        return itemId(owner);
    }

    // Admin phải trả lời đủ mọi mục checklist đang dùng, theo id mục (#279).
    private List<ChecklistAnswer> allPassedAnswers() {
        return configService.checklistItems().stream()
                .map(checklistItem -> new ChecklistAnswer(checklistItem.id(), true))
                .toList();
    }

    private String statusOf(long itemId) {
        return jdbc.sql("SELECT status FROM items WHERE id = :id")
                .param("id", itemId)
                .query(String.class)
                .single();
    }

    private String offerTypeOf(long itemId) {
        return jdbc.sql("SELECT offer_type FROM items WHERE id = :id")
                .param("id", itemId)
                .query(String.class)
                .single();
    }

    private int ledgerCount(long owner) {
        return jdbc.sql("SELECT count(*) FROM credit_ledger WHERE user_id = :owner")
                .param("owner", owner)
                .query(Integer.class)
                .single();
    }

    private String feeStateOf(long itemId) {
        return jdbc.sql("SELECT fee_state FROM items WHERE id = :id")
                .param("id", itemId)
                .query(String.class)
                .single();
    }

    private int pendingFeeOf(long itemId) {
        return jdbc.sql("SELECT pending_fee FROM items WHERE id = :id")
                .param("id", itemId)
                .query(Integer.class)
                .single();
    }

    private int balance(long owner) {
        return jdbc.sql("SELECT credit_balance FROM users WHERE id = :owner")
                .param("owner", owner)
                .query(Integer.class)
                .single();
    }

    private long itemId(long owner) {
        return jdbc.sql("SELECT id FROM items WHERE donor_id = :owner ORDER BY id DESC LIMIT 1")
                .param("owner", owner)
                .query(Long.class)
                .single();
    }

    private int held(long owner) {
        return jdbc.sql("SELECT held_credit FROM users WHERE id = :owner")
                .param("owner", owner)
                .query(Integer.class)
                .single();
    }

    private void assertNoItemOrHold(long owner, long[] images) {
        assertThat(jdbc.sql("SELECT count(*) FROM items WHERE donor_id = :owner")
                        .param("owner", owner)
                        .query(Integer.class)
                        .single())
                .isZero();
        assertThat(held(owner)).isZero();
        for (long image : images) {
            assertThat(jdbc.sql("SELECT status FROM media_files WHERE id = :id")
                            .param("id", image)
                            .query(String.class)
                            .single())
                    .isEqualTo("UPLOADED");
        }
    }
}
