package com.shareloop.request;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.integration.storage.StorageService;
import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** D2: ảnh chat và ảnh giao nhận chỉ hai bên và Admin xem được, người khác nhận 404. */
@AutoConfigureMockMvc
@Transactional
class RequestMediaAccessIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    StorageService storage;

    long donorId;
    long receiverId;
    long strangerId;
    long adminId;
    long requestId;
    long otherRequestId;
    long chatMediaId;
    long handoverMediaId;

    @BeforeEach
    void setUp() throws Exception {
        donorId = user("mediaacc-d@shareloop.local", "0908100001", false);
        receiverId = user("mediaacc-r@shareloop.local", "0908100002", false);
        strangerId = user("mediaacc-s@shareloop.local", "0908100003", false);
        adminId = user("mediaacc-a@shareloop.local", "0908100004", true);
        long itemId = item(donorId);
        requestId = request(itemId, receiverId);
        otherRequestId = request(item(donorId), strangerId);
        chatMediaId = privateMedia("chat-private.jpg");
        handoverMediaId = privateMedia("handover-private.jpg");
        jdbc.sql("INSERT INTO messages (request_id, sender_id, kind, media_id) VALUES (:r, :s, 'IMAGE', :m)")
                .param("r", requestId)
                .param("s", receiverId)
                .param("m", chatMediaId)
                .update();
        jdbc.sql("INSERT INTO request_media_mappings (request_id, media_id, stage, uploaded_by) "
                        + "VALUES (:r, :m, 'HANDOVER', :u)")
                .param("r", requestId)
                .param("m", handoverMediaId)
                .param("u", donorId)
                .update();
        byte[] jpg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
        when(storage.read("chat-private.jpg")).thenReturn(jpg);
        when(storage.read("handover-private.jpg")).thenReturn(jpg);
    }

    @Test
    void readMedia_whenDonorOrReceiverOrAdmin_returnsImage() throws Exception {
        // when, then: hai bên và Admin xem được cả ảnh chat lẫn ảnh giao nhận
        for (long viewer : new long[] {donorId, receiverId, adminId}) {
            mvc.perform(get("/api/v1/requests/{id}/media/{mediaId}", requestId, chatMediaId)
                            .header("Authorization", token(viewer)))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/requests/{id}/media/{mediaId}", requestId, handoverMediaId)
                            .header("Authorization", token(viewer)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void readMedia_whenStranger_returnsNotFoundNotForbidden() throws Exception {
        // when, then: người ngoài nhận 404 để không lộ ảnh có tồn tại
        mvc.perform(get("/api/v1/requests/{id}/media/{mediaId}", requestId, chatMediaId)
                        .header("Authorization", token(strangerId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void readMedia_whenNotLoggedIn_returnsUnauthorized() throws Exception {
        // when, then
        mvc.perform(get("/api/v1/requests/{id}/media/{mediaId}", requestId, chatMediaId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readMedia_whenMediaBelongsToAnotherRequest_returnsNotFound() throws Exception {
        // given: người nhận của giao dịch khác thử đọc ảnh của giao dịch này qua id giao dịch của mình
        // when, then
        mvc.perform(get("/api/v1/requests/{id}/media/{mediaId}", otherRequestId, chatMediaId)
                        .header("Authorization", token(strangerId)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/requests/{id}/media/{mediaId}", otherRequestId, chatMediaId)
                        .header("Authorization", token(donorId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicMediaEndpoint_whenPrivateChatImage_returnsNotFound() throws Exception {
        // when, then: không còn xem được ảnh chat bằng đường công khai, kể cả khi biết id
        mvc.perform(get("/api/v1/media/{id}", chatMediaId)).andExpect(status().isNotFound());
    }

    private String token(long userId) {
        return "Bearer " + jwtService.issue(userId, userId == adminId);
    }

    private long user(String email, String phone, boolean admin) {
        return jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status, is_admin) "
                        + "VALUES (:email, 'unused', 'Test', :phone, 'ACTIVE', :admin) RETURNING id")
                .param("email", email)
                .param("phone", phone)
                .param("admin", admin)
                .query(Long.class)
                .single();
    }

    private long item(long donor) {
        return jdbc.sql("INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, "
                        + "condition, status, approved_at, post_fee_paid, fee_state, free_edit_used) "
                        + "VALUES (:donor, (SELECT id FROM item_categories ORDER BY id LIMIT 1), "
                        + "(SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1), 'GIVE', 'Bài kiểm ảnh', "
                        + "'Mô tả', 'GOOD', 'RESERVED', now(), true, 'CHARGED', true) RETURNING id")
                .param("donor", donor)
                .query(Long.class)
                .single();
    }

    private long request(long itemId, long receiver) {
        return jdbc.sql("INSERT INTO requests (item_id, receiver_id, type, status, reserved_at) "
                        + "VALUES (:item, :receiver, 'GIVE', 'LOGISTICS_CONFIRMED', now()) RETURNING id")
                .param("item", itemId)
                .param("receiver", receiver)
                .query(Long.class)
                .single();
    }

    private long privateMedia(String key) {
        return jdbc.sql("INSERT INTO media_files (storage_key, mime_type, size_bytes, visibility, status) "
                        + "VALUES (:key, 'image/jpeg', 4, 'PRIVATE', 'ATTACHED') RETURNING id")
                .param("key", key)
                .query(Long.class)
                .single();
    }
}
