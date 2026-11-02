package com.shareloop.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.repository.ItemRepository;
import com.shareloop.support.IntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test hồ sơ công khai: GET /users/{id}/profile, GET /users/{id}/items và chi tiết bài đã giao dịch.
 * DB PostgreSQL thật (Testcontainers); @Transactional nên dữ liệu tự rollback sau mỗi test.
 */
@AutoConfigureMockMvc
@Transactional
class PublicProfileIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ItemRepository items;

    @Autowired
    JwtService jwtService;

    long categoryId;
    long areaId;
    String areaName;
    long ownerId;
    long viewerId;
    String viewerToken;

    @BeforeEach
    void insertUsers() {
        categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        areaName = jdbc.sql("SELECT name FROM areas WHERE id = :id")
                .param("id", areaId)
                .query(String.class)
                .single();
        ownerId = insertUser("profile-owner@shareloop.local", "Nguyễn Văn An", "0900111001");
        viewerId = insertUser("profile-viewer@shareloop.local", "Trần Thị Bình", "0900111002");
        // 200 điểm là hạng Vàng theo ngưỡng mặc định (reputation_gold_min = 150), còn 4 sao
        jdbc.sql("UPDATE users SET area_id = :areaId, total_points = 200, trust_stars = 4 WHERE id = :id")
                .param("areaId", areaId)
                .param("id", ownerId)
                .update();
        viewerToken = jwtService.issue(viewerId, false);
    }

    @Test
    void getProfile_returnsNumbersAndHidesPrivateFields() throws Exception {
        // given: chủ hồ sơ cho 3 món (2 đúng mô tả, 1 không) và nhận 1 món của người xem
        givenTradedItem(ownerId, viewerId, "Món đã cho 1", true);
        givenTradedItem(ownerId, viewerId, "Món đã cho 2", true);
        givenTradedItem(ownerId, viewerId, "Món đã cho 3", false);
        givenTradedItem(viewerId, ownerId, "Món đã nhận", null);

        // when, then
        mvc.perform(get("/api/v1/users/{id}/profile", ownerId).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownerId))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.avatarInitials").value("NA"))
                .andExpect(jsonPath("$.district").value(areaName))
                .andExpect(jsonPath("$.rank").value("GOLD"))
                .andExpect(jsonPath("$.trustStars").value(4))
                .andExpect(jsonPath("$.asDescribedRate").value(Matchers.closeTo(0.6667, 0.001)))
                .andExpect(jsonPath("$.completedTransactions").value(4))
                .andExpect(jsonPath("$.joinedAt").exists())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.areaId").doesNotExist())
                .andExpect(jsonPath("$.totalPoints").doesNotExist())
                .andExpect(jsonPath("$.availableCredit").doesNotExist());
    }

    @Test
    void getProfile_withoutToken_returns401() throws Exception {
        // when, then
        mvc.perform(get("/api/v1/users/{id}/profile", ownerId)).andExpect(status().isUnauthorized());
    }

    @Test
    void getProfile_whenUserLocked_returns404() throws Exception {
        // given
        jdbc.sql("UPDATE users SET is_active = false WHERE id = :id")
                .param("id", ownerId)
                .update();

        // when, then
        mvc.perform(get("/api/v1/users/{id}/profile", ownerId).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mvc.perform(get("/api/v1/users/{id}/items", ownerId).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void getProfile_whenUserIsAdmin_returns404OnBothEndpoints() throws Exception {
        // given
        long adminId = insertUser("profile-admin@shareloop.local", "Quản Trị", "0900111004");
        jdbc.sql("UPDATE users SET is_admin = true WHERE id = :id")
                .param("id", adminId)
                .update();

        // when, then
        mvc.perform(get("/api/v1/users/{id}/profile", adminId).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mvc.perform(get("/api/v1/users/{id}/items", adminId).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void getProfile_whenUserMissing_returns404() throws Exception {
        // when, then
        mvc.perform(get("/api/v1/users/{id}/profile", 999_999_999L).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void listItems_activeTab_returnsOnlyPublicApprovedItems() throws Exception {
        // given: chỉ 2 bài đầu đang hiển thị công khai, các bài còn lại không được lộ ra
        givenApprovedItem("Bài công khai 1");
        givenApprovedItem("Bài công khai 2");
        items.saveAndFlush(newItem(ownerId, "Bài chờ duyệt"));
        Item rejected = newItem(ownerId, "Bài bị từ chối");
        rejected.transitionTo(ItemStatus.REJECTED);
        items.saveAndFlush(rejected);
        Item expired = givenApprovedItem("Bài hết hạn");
        jdbc.sql("UPDATE items SET expire_at = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE id = :id")
                .param("id", expired.getId())
                .update();
        Item hidden = newItem(ownerId, "Bài bị ẩn");
        hidden.approve();
        hidden.deactivate();
        items.saveAndFlush(hidden);
        givenTradedItem(ownerId, viewerId, "Bài đã giao dịch", null);

        // when, then
        mvc.perform(get("/api/v1/users/{id}/items", ownerId)
                        .param("tab", "ACTIVE")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[*].title")
                        .value(Matchers.containsInAnyOrder("Bài công khai 1", "Bài công khai 2")))
                .andExpect(jsonPath("$.items[0].requestable").value(true))
                .andExpect(jsonPath("$.items[0].completedAt").doesNotExist());
    }

    @Test
    void listItems_doneTab_returnsTradedItemsWithCompletedAt() throws Exception {
        // given
        givenApprovedItem("Bài công khai");
        givenTradedItem(ownerId, viewerId, "Bài đã giao dịch 1", null);
        givenTradedItem(ownerId, viewerId, "Bài đã giao dịch 2", null);

        // when, then
        mvc.perform(get("/api/v1/users/{id}/items", ownerId)
                        .param("tab", "DONE")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[*].status").value(Matchers.everyItem(Matchers.is("TRADED"))))
                .andExpect(jsonPath("$.items[*].completedAt").value(Matchers.everyItem(Matchers.notNullValue())))
                .andExpect(jsonPath("$.items[*].requestable").value(Matchers.everyItem(Matchers.is(false))));
    }

    @Test
    void listItems_withPageAndSize_returnsRequestedPage() throws Exception {
        // given
        givenApprovedItem("Bài 1");
        givenApprovedItem("Bài 2");
        givenApprovedItem("Bài 3");

        // when, then
        mvc.perform(get("/api/v1/users/{id}/items", ownerId)
                        .param("tab", "ACTIVE")
                        .param("page", "1")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void listItems_whenOwnerViewsOwnProfile_stillHidesPendingItems() throws Exception {
        // given
        givenApprovedItem("Bài công khai");
        items.saveAndFlush(newItem(ownerId, "Bài chờ duyệt"));
        String ownerToken = jwtService.issue(ownerId, false);

        // when, then
        mvc.perform(get("/api/v1/users/{id}/items", ownerId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Bài công khai"));
    }

    @Test
    void getItemDetail_whenTraded_isReadOnlyForOtherUsers() throws Exception {
        // given
        long tradedItemId = givenTradedItem(ownerId, viewerId, "Bài đã giao dịch", null);

        // when, then
        mvc.perform(get("/api/v1/items/{id}", tradedItemId).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRADED"))
                .andExpect(jsonPath("$.requestable").value(false))
                .andExpect(jsonPath("$.completedAt").exists())
                .andExpect(jsonPath("$.receiverId").doesNotExist());
    }

    @Test
    void getItemDetail_whenPendingReview_stillNotFoundForOtherUsers() throws Exception {
        // given
        Item pending = items.saveAndFlush(newItem(ownerId, "Bài chờ duyệt"));

        // when, then
        mvc.perform(get("/api/v1/items/{id}", pending.getId()).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
    }

    @Test
    void createRequest_whenItemTraded_throwsItemNotAvailable() throws Exception {
        // given
        long tradedItemId = givenTradedItem(ownerId, viewerId, "Bài đã giao dịch", null);
        long otherUserId = insertUser("profile-other@shareloop.local", "Lê Văn Cường", "0900111003");
        String otherToken = jwtService.issue(otherUserId, false);

        // when, then: gọi thẳng API tạo yêu cầu vẫn bị service chặn
        mvc.perform(post("/api/v1/items/{id}/requests", tradedItemId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ITEM_NOT_AVAILABLE"));
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

    private Item newItem(long donorId, String title) {
        return new Item(
                donorId,
                categoryId,
                areaId,
                OfferType.GIVE,
                title,
                "Mô tả đủ dài cho bài kiểm thử hồ sơ công khai",
                null,
                ItemCondition.GOOD,
                null,
                null);
    }

    private Item givenApprovedItem(String title) {
        Item item = newItem(ownerId, title);
        item.approve();
        return items.saveAndFlush(item);
    }

    // Bài của donorId đã TRADED, kèm một Request COMPLETED với receiverId; asDescribed null là chưa đánh giá.
    private long givenTradedItem(long donorId, long receiverId, String title, Boolean asDescribed) {
        Item item = newItem(donorId, title);
        item.approve();
        item.markTraded();
        items.saveAndFlush(item);
        jdbc.sql("INSERT INTO requests (item_id, receiver_id, type, status, completed_at, as_described) "
                        + "VALUES (:itemId, :receiverId, 'GIVE', 'COMPLETED', CURRENT_TIMESTAMP, :asDescribed)")
                .param("itemId", item.getId())
                .param("receiverId", receiverId)
                .param("asDescribed", asDescribed)
                .update();
        return item.getId();
    }
}
