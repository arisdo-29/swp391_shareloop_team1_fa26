package com.shareloop.request;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.repository.ItemRepository;
import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test các trường phục vụ màn Tin nhắn và Tiến trình giao dịch trong RequestResponse: ảnh đại diện hai
 * bên, lịch hẹn, thời điểm hoàn tất, ảnh giao nhận, cờ disputeExpired. DB PostgreSQL thật (Testcontainers);
 * @Transactional nên dữ liệu tự rollback sau mỗi test.
 */
@AutoConfigureMockMvc
@Transactional
class RequestProgressInfoIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ItemRepository items;

    @Autowired
    JwtService jwtService;

    long donorId;
    long receiverId;
    long donorAvatarMediaId;
    String receiverToken;

    @BeforeEach
    void insertUsers() {
        donorId = insertUser("progress-donor@shareloop.local", "Nguyễn Văn An", "0900222001");
        receiverId = insertUser("progress-receiver@shareloop.local", "Trần Thị Bình", "0900222002");
        donorAvatarMediaId = insertMedia("progress-avatar.jpg");
        jdbc.sql("UPDATE users SET avatar_media_id = :mediaId WHERE id = :id")
                .param("mediaId", donorAvatarMediaId)
                .param("id", donorId)
                .update();
        receiverToken = jwtService.issue(receiverId, false);
    }

    @Test
    void getDetail_whenCompletedLongAgo_returnsScheduleMediaAndDisputeExpired() throws Exception {
        // given: hoàn tất 48 giờ trước, quá hạn khiếu nại mặc định 24 giờ (complaint_window_hours)
        long requestId = givenCompletedRequest("48 hours");
        long photoId = insertMedia("progress-handover.jpg");
        jdbc.sql("INSERT INTO request_media_mappings (request_id, media_id, stage, uploaded_by) "
                        + "VALUES (:requestId, :mediaId, 'HANDOVER', :uploadedBy)")
                .param("requestId", requestId)
                .param("mediaId", photoId)
                .param("uploadedBy", donorId)
                .update();

        // when, then
        mvc.perform(get("/api/v1/requests/{id}", requestId).header("Authorization", "Bearer " + receiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingPlace").value("Cổng chính công viên"))
                .andExpect(jsonPath("$.meetingTime").exists())
                .andExpect(jsonPath("$.completedAt").exists())
                .andExpect(jsonPath("$.complaintDeadline").exists())
                .andExpect(jsonPath("$.disputeExpired").value(true))
                .andExpect(jsonPath("$.donorAvatarUrl").value("/api/v1/media/" + donorAvatarMediaId))
                .andExpect(jsonPath("$.receiverAvatarUrl").doesNotExist())
                .andExpect(jsonPath("$.handoverMedia.length()").value(1))
                .andExpect(jsonPath("$.handoverMedia[0].mediaId").value(photoId))
                .andExpect(jsonPath("$.handoverMedia[0].stage").value("HANDOVER"))
                .andExpect(jsonPath("$.handoverMedia[0].createdAt").exists());
    }

    @Test
    void getDetail_whenCompletedRecently_disputeNotExpired() throws Exception {
        // given: hoàn tất 1 giờ trước, vẫn trong hạn khiếu nại
        long requestId = givenCompletedRequest("1 hour");

        // when, then
        mvc.perform(get("/api/v1/requests/{id}", requestId).header("Authorization", "Bearer " + receiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disputeExpired").value(false))
                .andExpect(jsonPath("$.handoverMedia.length()").value(0));
    }

    @Test
    void getDetail_whenNotCompleted_disputeNotExpired() throws Exception {
        // given
        long itemId = givenItem("Bài đang giao dịch");
        long requestId = jdbc.sql("INSERT INTO requests (item_id, receiver_id, type, status) "
                        + "VALUES (:itemId, :receiverId, 'GIVE', 'PENDING') RETURNING id")
                .param("itemId", itemId)
                .param("receiverId", receiverId)
                .query(Long.class)
                .single();

        // when, then
        mvc.perform(get("/api/v1/requests/{id}", requestId).header("Authorization", "Bearer " + receiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.disputeExpired").value(false));
    }

    @Test
    void listMyRequests_returnsAvatarsWithoutHandoverMedia() throws Exception {
        // given
        givenCompletedRequest("48 hours");

        // when, then: danh sách có ảnh đại diện và cờ, không kèm ảnh giao nhận
        mvc.perform(get("/api/v1/me/requests").header("Authorization", "Bearer " + receiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].donorAvatarUrl").value("/api/v1/media/" + donorAvatarMediaId))
                .andExpect(jsonPath("$[0].disputeExpired").value(true))
                .andExpect(jsonPath("$[0].handoverMedia").doesNotExist());
    }

    @Test
    void getDetail_whenOutsider_returns403() throws Exception {
        // given
        long requestId = givenCompletedRequest("1 hour");
        long outsiderId = insertUser("progress-outsider@shareloop.local", "Lê Văn Cường", "0900222003");
        String outsiderToken = jwtService.issue(outsiderId, false);

        // when, then: người ngoài không xem được chi tiết, nên cũng không thấy ảnh giao nhận
        mvc.perform(get("/api/v1/requests/{id}", requestId).header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REQUEST_FORBIDDEN"));
    }

    private long insertUser(String email, String fullName, String phone) {
        return jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status) "
                        + "VALUES (:email, 'unused', :fullName, :phone, 'ACTIVE') RETURNING id")
                .param("email", email)
                .param("fullName", fullName)
                .param("phone", phone)
                .query(Long.class)
                .single();
    }

    private long insertMedia(String storageKey) {
        return jdbc.sql("INSERT INTO media_files (storage_key, mime_type, size_bytes, status) "
                        + "VALUES (:storageKey, 'image/jpeg', 100, 'ATTACHED') RETURNING id")
                .param("storageKey", storageKey)
                .query(Long.class)
                .single();
    }

    private long givenItem(String title) {
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        Item item = new Item(
                donorId, categoryId, areaId, OfferType.GIVE, title, "Mô tả", null, ItemCondition.GOOD, null, null);
        item.approve();
        return items.saveAndFlush(item).getId();
    }

    // Request COMPLETED có lịch hẹn, hoàn tất cách đây `completedAgo` (khoảng thời gian PostgreSQL, ví dụ "48 hours").
    private long givenCompletedRequest(String completedAgo) {
        long itemId = givenItem("Bài đã giao dịch");
        return jdbc.sql("INSERT INTO requests (item_id, receiver_id, type, status, meeting_place, meeting_time, "
                        + "completed_at) VALUES (:itemId, :receiverId, 'GIVE', 'COMPLETED', 'Cổng chính công viên', "
                        + "CURRENT_TIMESTAMP - CAST(:ago AS interval) - INTERVAL '1 hour', "
                        + "CURRENT_TIMESTAMP - CAST(:ago AS interval)) RETURNING id")
                .param("itemId", itemId)
                .param("receiverId", receiverId)
                .param("ago", completedAgo)
                .query(Long.class)
                .single();
    }
}
