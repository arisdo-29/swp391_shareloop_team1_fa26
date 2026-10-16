package com.shareloop.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shareloop.chat.ChatErrorCode;
import com.shareloop.chat.dto.MessageResponse;
import com.shareloop.chat.dto.SendMessageRequest;
import com.shareloop.chat.entity.MessageKind;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration test cho nghiệp vụ khoá và gửi ảnh trong chat (FR-75, BR-C05; Issue #111).
 */
class ChatServiceIT extends IntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    ChatService chatService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> createdMessageIds = new ArrayList<>();
    private final List<Long> createdRequestIds = new ArrayList<>();
    private final List<Long> createdItemIds = new ArrayList<>();
    private final List<Long> createdMediaIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long messageId : createdMessageIds) {
            jdbc.update("DELETE FROM messages WHERE id = ?", messageId);
        }
        createdMessageIds.clear();

        for (Long requestId : createdRequestIds) {
            jdbc.update("DELETE FROM messages WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM requests WHERE id = ?", requestId);
        }
        createdRequestIds.clear();

        for (Long itemId : createdItemIds) {
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        createdItemIds.clear();

        for (Long mediaId : createdMediaIds) {
            jdbc.update("DELETE FROM media_files WHERE id = ?", mediaId);
        }
        createdMediaIds.clear();

        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUserIds.clear();
    }

    @Test
    @DisplayName("Gửi ảnh trước LogisticsConfirmed bị ném lỗi CHAT_IMAGE_BEFORE_LOGISTICS_FORBIDDEN (FR-75, BR-C05)")
    void sendMessage_image_beforeLogisticsConfirmed_throwsException() {
        long donorId = insertUser();
        long receiverId = insertUser();
        long itemId = insertItem(donorId, "RESERVED");
        long requestId = insertRequest(itemId, receiverId, "RESERVED");
        long mediaId = insertMedia(receiverId, "UPLOADED");

        SendMessageRequest request = new SendMessageRequest("Ảnh thực tế", mediaId);

        assertThatThrownBy(() -> chatService.sendMessage(requestId, receiverId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChatErrorCode.CHAT_IMAGE_BEFORE_LOGISTICS_FORBIDDEN));

        // Kiểm tra media vẫn ở trạng thái UPLOADED, chưa bị attach
        String mediaStatus = jdbc.queryForObject("SELECT status FROM media_files WHERE id = ?", String.class, mediaId);
        assertThat(mediaStatus).isEqualTo("UPLOADED");
    }

    @Test
    @DisplayName("Gửi ảnh từ LogisticsConfirmed trở đi thành công và media chuyển sang ATTACHED")
    void sendMessage_image_whenLogisticsConfirmed_succeedsAndAttachesMedia() {
        long donorId = insertUser();
        long receiverId = insertUser();
        long itemId = insertItem(donorId, "RESERVED");
        long requestId = insertRequest(itemId, receiverId, "LOGISTICS_CONFIRMED");
        long mediaId = insertMedia(receiverId, "UPLOADED");

        SendMessageRequest request = new SendMessageRequest("Gói đồ đây bạn nhé", mediaId);

        MessageResponse response = chatService.sendMessage(requestId, receiverId, request);

        assertThat(response).isNotNull();
        assertThat(response.kind()).isEqualTo(MessageKind.IMAGE.name());
        assertThat(response.mediaId()).isEqualTo(mediaId);
        assertThat(response.content()).isEqualTo("Gói đồ đây bạn nhé");
        createdMessageIds.add(response.id());

        // Kiểm tra media file đã được attach sang trạng thái ATTACHED
        String mediaStatus = jdbc.queryForObject("SELECT status FROM media_files WHERE id = ?", String.class, mediaId);
        assertThat(mediaStatus).isEqualTo("ATTACHED");
    }

    // ---------- Tiện ích hỗ trợ test ----------

    private long insertUser() {
        int seq = PHONE_SEQUENCE.incrementAndGet();
        String email = "chat-it-" + seq + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0996%06d", seq);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'hash', 'Chat IT User', ?, 'ACTIVE', 0, 0) RETURNING id",
                Long.class,
                email,
                phone);
        createdUserIds.add(userId);
        return userId;
    }

    private long insertItem(long donorId, String status) {
        Long itemId = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Chat IT Item', 'Mô tả', 'NEW', ?) RETURNING id",
                Long.class,
                donorId,
                seedCategoryId(),
                seedAreaId(),
                status);
        createdItemIds.add(itemId);
        return itemId;
    }

    private long insertRequest(long itemId, long receiverId, String status) {
        Long requestId = jdbc.queryForObject(
                "INSERT INTO requests (item_id, receiver_id, type, status, reserved_at)"
                        + " VALUES (?, ?, 'GIVE', ?, ?) RETURNING id",
                Long.class,
                itemId,
                receiverId,
                status,
                Timestamp.from(Instant.now()));
        createdRequestIds.add(requestId);
        return requestId;
    }

    private long insertMedia(long userId, String status) {
        Long mediaId = jdbc.queryForObject(
                "INSERT INTO media_files (storage_key, mime_type, size_bytes, visibility, status, created_by)"
                        + " VALUES (?, 'image/jpeg', 1024, 'PUBLIC', ?, ?) RETURNING id",
                Long.class,
                "test-storage-key-" + System.nanoTime(),
                status,
                userId);
        createdMediaIds.add(mediaId);
        return mediaId;
    }

    private long seedCategoryId() {
        return jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
    }

    private long seedAreaId() {
        return jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
    }
}
