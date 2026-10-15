package com.shareloop.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ItemImagesIT extends IntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcClient jdbc;

    @Test
    void createItemKeepsUploadOrderAndRejectsForeignMedia() throws Exception {
        long userId = createUser("item-images-owner@shareloop.local", "0900000223");
        long otherUserId = createUser("item-images-other@shareloop.local", "0900000224");
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long first = createMedia(userId);
        long second = createMedia(userId);
        long third = createMedia(userId);
        long fourth = createMedia(userId);
        long fifth = createMedia(userId);
        long foreign = createMedia(otherUserId);

        mvc.perform(post("/api/v1/items")
                        .header("Authorization", "Bearer " + jwtService.issue(userId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(categoryId, areaId, second, first, third)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images[0]").value("/api/v1/media/" + second))
                .andExpect(jsonPath("$.images[1]").value("/api/v1/media/" + first))
                .andExpect(jsonPath("$.images[2]").value("/api/v1/media/" + third));

        long itemId = jdbc.sql("SELECT id FROM items WHERE donor_id = :userId ORDER BY id DESC LIMIT 1")
                .param("userId", userId)
                .query(Long.class)
                .single();
        assertThat(jdbc.sql("SELECT media_id FROM item_media_mappings WHERE item_id = :itemId ORDER BY sort_order")
                        .param("itemId", itemId)
                        .query(Long.class)
                        .list())
                .containsExactly(second, first, third);

        mvc.perform(post("/api/v1/items")
                        .header("Authorization", "Bearer " + jwtService.issue(userId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(categoryId, areaId, fourth, fifth, foreign)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_OWNER"));
        // This test runs inside the test transaction. ItemCreateIT verifies the committed
        // rollback boundary for a failed attach without an enclosing test transaction.
        assertThat(jdbc.sql("SELECT status FROM media_files WHERE id = :id")
                        .param("id", foreign)
                        .query(String.class)
                        .single())
                .isEqualTo("UPLOADED");
    }

    @Test
    void twoImagesReturnUnprocessableContentAndImagesField() throws Exception {
        long userId = createUser("item-images-count@shareloop.local", "0900000225");
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();

        mvc.perform(post("/api/v1/items")
                        .header("Authorization", "Bearer " + jwtService.issue(userId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(categoryId, areaId, 1, 2)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ITEM_INVALID_IMAGE_COUNT"))
                .andExpect(jsonPath("$.field").value("images"));
    }

    @Test
    void defectiveItemRequiresDefectImageAndPersistsItsRole() throws Exception {
        long userId = createUser("item-defect-owner@shareloop.local", "0900000226");
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long first = createMedia(userId);
        long second = createMedia(userId);
        long third = createMedia(userId);
        long defect = createMedia(userId);
        String body = "{\"categoryId\":%d,\"areaId\":%d,\"offerType\":\"GIVE\",\"title\":\"Sách cũ\","
                + "\"description\":\"Còn dùng được\",\"condition\":\"DEFECTIVE\",\"defectNote\":\"Rách bìa\","
                + "\"images\":[\"/api/v1/media/%d\",\"/api/v1/media/%d\",\"/api/v1/media/%d\"]%s}";

        mvc.perform(post("/api/v1/items")
                        .header("Authorization", "Bearer " + jwtService.issue(userId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(categoryId, areaId, first, second, third, "")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ITEM_DEFECT_IMAGES_REQUIRED"))
                .andExpect(jsonPath("$.field").value("defectImages"));

        mvc.perform(post("/api/v1/items")
                        .header("Authorization", "Bearer " + jwtService.issue(userId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(
                                categoryId,
                                areaId,
                                first,
                                second,
                                third,
                                ",\"defectImages\":[\"/api/v1/media/" + defect + "\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.defectImages[0]").value("/api/v1/media/" + defect));

        long itemId = jdbc.sql("SELECT id FROM items WHERE donor_id = :userId ORDER BY id DESC LIMIT 1")
                .param("userId", userId)
                .query(Long.class)
                .single();
        assertThat(jdbc.sql("SELECT role FROM item_media_mappings WHERE item_id = :itemId AND media_id = :mediaId")
                        .param("itemId", itemId)
                        .param("mediaId", defect)
                        .query(String.class)
                        .single())
                .isEqualTo("DEFECT");
        mvc.perform(get("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(userId, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.defectImages[0]").value("/api/v1/media/" + defect));
    }

    @Test
    void electronicItemRequiresOperatingStatusAndStoresAttribute() throws Exception {
        long userId = createUser("item-electronic-owner@shareloop.local", "0900000227");
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE name = 'Điện tử và phụ kiện'")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long first = createMedia(userId);
        long second = createMedia(userId);
        long third = createMedia(userId);
        String body = body(categoryId, areaId, first, second, third);

        mvc.perform(post("/api/v1/items")
                        .header("Authorization", "Bearer " + jwtService.issue(userId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ITEM_OPERATING_STATUS_REQUIRED"))
                .andExpect(jsonPath("$.field").value("operatingStatus"));

        mvc.perform(post("/api/v1/items")
                        .header("Authorization", "Bearer " + jwtService.issue(userId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.strip().replaceFirst("}$", ",\"operatingStatus\":\"PARTIAL\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operatingStatus").value("PARTIAL"));

        long itemId = jdbc.sql("SELECT id FROM items WHERE donor_id = :userId ORDER BY id DESC LIMIT 1")
                .param("userId", userId)
                .query(Long.class)
                .single();
        assertThat(jdbc.sql("SELECT m.value FROM item_attribute_mappings m "
                                + "JOIN item_attributes a ON a.id = m.attribute_id "
                                + "WHERE m.item_id = :itemId AND a.name = 'Trạng thái hoạt động'")
                        .param("itemId", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("PARTIAL");
        mvc.perform(get("/api/v1/items/{id}", itemId)
                        .header("Authorization", "Bearer " + jwtService.issue(userId, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatingStatus").value("PARTIAL"));
    }

    @Test
    void unrelatedConditionAndCategoryRejectExtraFields() throws Exception {
        long userId = createUser("item-extra-fields@shareloop.local", "0900000228");
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long first = createMedia(userId);
        long second = createMedia(userId);
        long third = createMedia(userId);
        long defect = createMedia(userId);
        String base = body(categoryId, areaId, first, second, third).strip();

        mvc.perform(post("/api/v1/items")
                        .header("Authorization", "Bearer " + jwtService.issue(userId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(base.replaceFirst("}$", ",\"defectImages\":[\"/api/v1/media/" + defect + "\"]}")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("defectImages"));

        mvc.perform(post("/api/v1/items")
                        .header("Authorization", "Bearer " + jwtService.issue(userId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(base.replaceFirst("}$", ",\"operatingStatus\":\"NORMAL\"}")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("operatingStatus"));
    }

    private long createUser(String email, String phone) {
        return jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance) "
                        + "VALUES (:email, 'unused', 'Test', :phone, 'ACTIVE', 100) RETURNING id")
                .param("email", email)
                .param("phone", phone)
                .query(Long.class)
                .single();
    }

    private long createMedia(long ownerId) {
        return jdbc.sql("INSERT INTO media_files (storage_key, mime_type, size_bytes, created_by) "
                        + "VALUES (gen_random_uuid()::text, 'image/png', 10, :ownerId) RETURNING id")
                .param("ownerId", ownerId)
                .query(Long.class)
                .single();
    }

    private String body(long categoryId, long areaId, long... mediaIds) {
        String images = java.util.Arrays.stream(mediaIds)
                .mapToObj(id -> "\"/api/v1/media/" + id + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return """
                {"categoryId":%d,"areaId":%d,"offerType":"GIVE","title":"Sách",\
                "description":"Sách còn tốt","condition":"GOOD","images":[%s]}
                """.formatted(categoryId, areaId, images);
    }
}
