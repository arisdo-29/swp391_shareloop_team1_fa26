package com.shareloop.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ItemEditIT extends IntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EntityManager entityManager;

    @Test
    void paidEditResubmitsItemAndReplacesImagesInOrder() throws Exception {
        long ownerId = user("item-edit-owner@shareloop.local", "0900000751");
        long itemId = approvedItem(ownerId);
        long oldImage = media(ownerId, "ATTACHED");
        long first = media(ownerId, "UPLOADED");
        long second = media(ownerId, "UPLOADED");
        long third = media(ownerId, "UPLOADED");
        jdbc.sql(
                        "INSERT INTO item_media_mappings (item_id, media_id, role, sort_order) VALUES (:itemId, :mediaId, 'PHOTO', 0)")
                .param("itemId", itemId)
                .param("mediaId", oldImage)
                .update();

        mvc.perform(put("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(ownerId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Áo khoác mới", "Còn sử dụng tốt", first, second, third)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.images[0]").value("/api/v1/media/" + first))
                .andExpect(jsonPath("$.images[1]").value("/api/v1/media/" + second))
                .andExpect(jsonPath("$.images[2]").value("/api/v1/media/" + third));

        entityManager.flush();

        assertThat(jdbc.sql("SELECT status FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("PENDING_REVIEW");

        assertThat(jdbc.sql("SELECT media_id FROM item_media_mappings WHERE item_id = :itemId ORDER BY sort_order")
                        .param("itemId", itemId)
                        .query(Long.class)
                        .list())
                .containsExactly(first, second, third);
        assertThat(jdbc.sql("SELECT status FROM media_files WHERE id = :id")
                        .param("id", oldImage)
                        .query(String.class)
                        .single())
                .isEqualTo("UPLOADED");
        assertThat(jdbc.sql("SELECT fee_state FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("HELD");
        assertThat(jdbc.sql("SELECT held_credit FROM users WHERE id = :id")
                        .param("id", ownerId)
                        .query(Integer.class)
                        .single())
                .isPositive();
    }

    @Test
    void updateItem_whenUnpaidGiveChangesToSwap_holdsPostFee() throws Exception {
        // Bài Cho tặng đã duyệt nhưng chưa từng trả phí đăng Trao đổi.
        long ownerId = user("item-edit-owner8@shareloop.local", "0900000761");
        jdbc.sql("UPDATE users SET credit_balance = 20 WHERE id = :id")
                .param("id", ownerId)
                .update();
        long itemId = approvedItem(ownerId);
        String swapBody = body("Tên mới", "Mô tả mới")
                .replace("\"offerType\":\"GIVE\"", "\"offerType\":\"SWAP\",\"desiredItem\":\"Sách khác\"");

        // when
        mvc.perform(put("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(ownerId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(swapBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        assertThat(jdbc.sql("SELECT offer_type FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("SWAP");
        assertThat(jdbc.sql("SELECT held_credit FROM users WHERE id = :id")
                        .param("id", ownerId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(5);
        assertThat(jdbc.sql("SELECT pending_fee FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(5);
    }

    @Test
    void otherUserCannotEdit() throws Exception {
        long ownerId = user("item-edit-owner2@shareloop.local", "0900000752");
        long otherId = user("item-edit-other@shareloop.local", "0900000753");
        long itemId = approvedItem(ownerId);
        mvc.perform(put("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(otherId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Tên mới", "Mô tả mới")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_OWNER"));
    }

    @Test
    void reservedRequestBlocksEditBeforeFeeHold() throws Exception {
        long ownerId = user("item-edit-owner3@shareloop.local", "0900000754");
        long receiverId = user("item-edit-receiver@shareloop.local", "0900000755");
        long itemId = approvedItem(ownerId);
        jdbc.sql(
                        "INSERT INTO requests (item_id, receiver_id, type, status) VALUES (:itemId, :receiverId, 'GIVE', 'RESERVED')")
                .param("itemId", itemId)
                .param("receiverId", receiverId)
                .update();

        mvc.perform(put("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(ownerId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Tên mới", "Mô tả mới")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_EDIT_NOT_ALLOWED"));
        assertThat(jdbc.sql("SELECT held_credit FROM users WHERE id = :id")
                        .param("id", ownerId)
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    @Test
    void tierOneBlockDoesNotHoldFeeOrChangeItem() throws Exception {
        long ownerId = user("item-edit-owner4@shareloop.local", "0900000756");
        long itemId = approvedItem(ownerId);
        mvc.perform(put("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(ownerId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Tên mới", "Liên hệ 0901234567")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ITEM_MODERATION_BLOCKED"));
        assertThat(jdbc.sql("SELECT status FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("APPROVED");
        assertThat(jdbc.sql("SELECT held_credit FROM users WHERE id = :id")
                        .param("id", ownerId)
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    @Test
    void foreignImageRollsBackFeeAndContent() throws Exception {
        long ownerId = user("item-edit-owner5@shareloop.local", "0900000757");
        long otherId = user("item-edit-other5@shareloop.local", "0900000758");
        long itemId = approvedItem(ownerId);
        long first = media(ownerId, "UPLOADED");
        long second = media(ownerId, "UPLOADED");
        long foreign = media(otherId, "UPLOADED");

        mvc.perform(put("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(ownerId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Tên mới", "Mô tả mới", first, second, foreign)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_OWNER"));

        assertThat(jdbc.sql("SELECT held_credit FROM users WHERE id = :id")
                        .param("id", ownerId)
                        .query(Integer.class)
                        .single())
                .isZero();
        assertThat(jdbc.sql("SELECT status FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("APPROVED");
    }

    @Test
    void pendingEditOverwritesWithoutHoldingAnotherFeeOrChangingImages() throws Exception {
        long ownerId = user("item-edit-owner6@shareloop.local", "0900000759");
        long itemId = approvedItem(ownerId);
        long imageId = media(ownerId, "ATTACHED");
        jdbc.sql(
                        "INSERT INTO item_media_mappings (item_id, media_id, role, sort_order) VALUES (:itemId, :mediaId, 'PHOTO', 0)")
                .param("itemId", itemId)
                .param("mediaId", imageId)
                .update();
        jdbc.sql(
                        "UPDATE items SET status = 'PENDING_REVIEW', fee_state = 'HELD', pending_fee = 5, pending_fee_type = 'EDIT' WHERE id = :id")
                .param("id", itemId)
                .update();
        jdbc.sql("UPDATE users SET held_credit = 5 WHERE id = :id")
                .param("id", ownerId)
                .update();

        mvc.perform(put("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(ownerId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Tên sửa tiếp", "Mô tả mới")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.images[0]").value("/api/v1/media/" + imageId));

        assertThat(jdbc.sql("SELECT held_credit FROM users WHERE id = :id")
                        .param("id", ownerId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(5);
        assertThat(jdbc.sql("SELECT count(*) FROM item_media_mappings WHERE item_id = :id")
                        .param("id", itemId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void editRequiresDefectImageAndElectronicOperatingStatusBeforeFeeHold() throws Exception {
        long ownerId = user("item-edit-owner7@shareloop.local", "0900000760");
        long itemId = approvedItem(ownerId);
        String defectiveBody = body("Tên mới", "Mô tả mới")
                .replace("\"condition\":\"GOOD\"", "\"condition\":\"DEFECTIVE\",\"defectNote\":\"Nứt vỏ\"");
        mvc.perform(put("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(ownerId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defectiveBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("defectImages"));

        long electronicCategoryId = jdbc.sql("SELECT id FROM item_categories WHERE name = 'Điện tử và phụ kiện'")
                .query(Long.class)
                .single();
        String electronicBody =
                body("Tên mới", "Mô tả mới").replace("\"categoryId\":1", "\"categoryId\":" + electronicCategoryId);
        mvc.perform(put("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(ownerId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(electronicBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("operatingStatus"));
        assertThat(jdbc.sql("SELECT held_credit FROM users WHERE id = :id")
                        .param("id", ownerId)
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    private long user(String email, String phone) {
        return jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance) "
                        + "VALUES (:email, 'unused', 'Test', :phone, 'ACTIVE', 20) RETURNING id")
                .param("email", email)
                .param("phone", phone)
                .query(Long.class)
                .single();
    }

    private long approvedItem(long ownerId) {
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        return jdbc.sql("INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, "
                        + "status, approved_at, post_fee_paid, fee_state, free_edit_used) "
                        + "VALUES (:ownerId, :categoryId, :areaId, 'GIVE', 'Tên cũ', 'Mô tả cũ', 'GOOD', "
                        + "'APPROVED', now(), false, 'CHARGED', true) RETURNING id")
                .param("ownerId", ownerId)
                .param("categoryId", categoryId)
                .param("areaId", areaId)
                .query(Long.class)
                .single();
    }

    private long media(long ownerId, String status) {
        return jdbc.sql("INSERT INTO media_files (storage_key, mime_type, size_bytes, created_by, status) "
                        + "VALUES (gen_random_uuid()::text, 'image/png', 10, :ownerId, :status) RETURNING id")
                .param("ownerId", ownerId)
                .param("status", status)
                .query(Long.class)
                .single();
    }

    private String body(String title, String description, long... imageIds) {
        String images = java.util.Arrays.stream(imageIds)
                .mapToObj(id -> "\"/api/v1/media/" + id + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        String imageField = imageIds.length == 0 ? "" : ",\"images\":[" + images + "]";
        return "{\"categoryId\":1,\"areaId\":2,\"offerType\":\"GIVE\",\"title\":\"%s\",\"description\":\"%s\",\"condition\":\"GOOD\"%s}"
                .formatted(title, description, imageField);
    }
}
