package com.shareloop.user;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/v1/admin/users (FR-107) chạy trên PostgreSQL thật để kiểm cả câu JPQL lọc có tham số null. Mọi tài khoản
 * của test có email đuôi @zzlist.test nên dùng keyword "zzlist" để không lẫn với dữ liệu seed.
 */
@AutoConfigureMockMvc
@Transactional // mỗi test tự rollback, không để lại tài khoản
class AdminUserListIT extends IntegrationTest {

    private static final String LIST_URL = "/api/v1/admin/users";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcClient jdbc;

    private long adminId;
    private String adminToken;

    @BeforeEach
    void createAccounts() {
        insertUser("active@zzlist.test", "Lan An", "0911000001", "ACTIVE", true, false);
        insertUser("locked@zzlist.test", "Bình Khóa", "0911000002", "ACTIVE", false, false);
        insertUser("pending@zzlist.test", "Chờ Xác Minh", "0911000003", "PENDING_VERIFICATION", true, false);
        insertUser("banned@zzlist.test", "Bị Cấm", "0911000004", "BANNED", true, false);
        adminId = insertUser("admin@zzlist.test", "Quản Trị", "0911000005", "ACTIVE", true, true);
        adminToken = "Bearer " + jwtService.issue(adminId, true);
    }

    @Test
    void list_withKeywordOnly_returnsAllAccountsNewestFirstWithDerivedFields() throws Exception {
        mvc.perform(get(LIST_URL).param("keyword", "zzlist").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                // tài khoản chèn sau cùng (admin) đứng đầu
                .andExpect(jsonPath("$.items[0].email").value("admin@zzlist.test"))
                .andExpect(jsonPath("$.items[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.items[?(@.email=='locked@zzlist.test')].status")
                        .value(hasItem("LOCKED")))
                .andExpect(jsonPath("$.items[?(@.email=='locked@zzlist.test')].locked")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.items[?(@.email=='active@zzlist.test')].status")
                        .value(hasItem("ACTIVE")))
                .andExpect(jsonPath("$.items[?(@.email=='active@zzlist.test')].role")
                        .value(hasItem("USER")));
    }

    @Test
    void list_filteredByState_returnsOnlyThatState() throws Exception {
        assertSingleEmail("LOCKED", "locked@zzlist.test");
        assertSingleEmail("PENDING_VERIFICATION", "pending@zzlist.test");
        assertSingleEmail("BANNED", "banned@zzlist.test");
        // ACTIVE gồm cả tài khoản Admin đang hoạt động, nhưng không có tài khoản khoá, chờ xác minh hay bị cấm
        mvc.perform(get(LIST_URL)
                        .param("keyword", "zzlist")
                        .param("status", "ACTIVE")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].email").value(not(hasItem("locked@zzlist.test"))));
    }

    @Test
    void list_filteredByRole_returnsOnlyAdmins() throws Exception {
        mvc.perform(get(LIST_URL)
                        .param("keyword", "zzlist")
                        .param("role", "ADMIN")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].email").value("admin@zzlist.test"));
    }

    @Test
    void list_keywordMatchesNameEmailAndPhoneIgnoringCase() throws Exception {
        assertKeywordFindsOnly("LAN AN", "active@zzlist.test");
        assertKeywordFindsOnly("BANNED@ZZLIST", "banned@zzlist.test");
        assertKeywordFindsOnly("0911000003", "pending@zzlist.test");
    }

    @Test
    void list_paginatesWithRequestedSize() throws Exception {
        mvc.perform(get(LIST_URL)
                        .param("keyword", "zzlist")
                        .param("size", "2")
                        .param("page", "2")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.page").value(2));
    }

    @Test
    void list_rejectsNormalUserAnonymousAndBadPaging() throws Exception {
        String userToken = "Bearer " + jwtService.issue(adminId, false);
        mvc.perform(get(LIST_URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(LIST_URL).header("Authorization", userToken)).andExpect(status().isForbidden());
        mvc.perform(get(LIST_URL).param("size", "0").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(get(LIST_URL).param("status", "UNKNOWN").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
    }

    private void assertSingleEmail(String state, String expectedEmail) throws Exception {
        mvc.perform(get(LIST_URL)
                        .param("keyword", "zzlist")
                        .param("status", state)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].email").value(expectedEmail));
    }

    private void assertKeywordFindsOnly(String keyword, String expectedEmail) throws Exception {
        mvc.perform(get(LIST_URL).param("keyword", keyword).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].email").value(expectedEmail));
    }

    private long insertUser(String email, String fullName, String phone, String status, boolean active, boolean admin) {
        return jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status, is_active, is_admin) "
                        + "VALUES (:email, 'unused', :fullName, :phone, :status, :active, :admin) RETURNING id")
                .param("email", email)
                .param("fullName", fullName)
                .param("phone", phone)
                .param("status", status)
                .param("active", active)
                .param("admin", admin)
                .query(Long.class)
                .single();
    }
}
