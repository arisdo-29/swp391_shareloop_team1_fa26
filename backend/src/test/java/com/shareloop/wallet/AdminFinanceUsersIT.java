package com.shareloop.wallet;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/v1/admin/finance/users và /users/{id}/wallet (FR-112) trên PostgreSQL thật. Tài khoản của test có email
 * đuôi @zzfin.test nên dùng keyword "zzfin" để không lẫn với dữ liệu khác.
 */
@AutoConfigureMockMvc
@Transactional // mỗi test tự rollback
class AdminFinanceUsersIT extends IntegrationTest {

    private static final String USERS_URL = "/api/v1/admin/finance/users";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcClient jdbc;

    private long anId;
    private long binhId;
    private String adminToken;

    @BeforeEach
    void createUsersAndOrders() {
        // An: 30 Credit, đang giữ 5 nên khả dụng 25. Đã nạp thành công 20 + 30, một đơn chờ 10 không tính.
        anId = insertUser("an@zzfin.test", "Lan An", "0922000001", 30, 5, false);
        insertOrder(anId, 20, "SUCCESS");
        insertOrder(anId, 30, "SUCCESS");
        insertOrder(anId, 10, "PENDING");
        // Bình: chưa nạp lần nào.
        binhId = insertUser("binh@zzfin.test", "Bình", "0922000002", 0, 0, false);
        // Admin: không được liệt kê trong danh sách tra cứu người dùng.
        long adminId = insertUser("admin@zzfin.test", "Quản Trị", "0922000003", 0, 0, true);
        adminToken = "Bearer " + jwtService.issue(adminId, true);
    }

    @Test
    void users_returnsBalancesAndToppedUpTotalsAndSkipsAdmins() throws Exception {
        mvc.perform(get(USERS_URL).param("keyword", "zzfin").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                // người dùng tạo sau đứng trước: Bình trước, An sau
                .andExpect(jsonPath("$.items[0].fullName").value("Bình"))
                .andExpect(jsonPath("$.items[0].totalToppedUpCredit").value(0))
                .andExpect(jsonPath("$.items[1].fullName").value("Lan An"))
                .andExpect(jsonPath("$.items[1].availableCredit").value(25))
                .andExpect(jsonPath("$.items[1].heldCredit").value(5))
                .andExpect(jsonPath("$.items[1].totalCredit").value(30))
                .andExpect(jsonPath("$.items[1].totalToppedUpCredit").value(50));
    }

    @Test
    void users_searchByNameFindsOnlyMatchingUser() throws Exception {
        mvc.perform(get(USERS_URL).param("keyword", "lan an").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].email").value("an@zzfin.test"));
    }

    @Test
    void userWallet_returnsContactAndCredit() throws Exception {
        mvc.perform(get(USERS_URL + "/{id}/wallet", anId).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Lan An"))
                .andExpect(jsonPath("$.phone").value("0922000001"))
                .andExpect(jsonPath("$.availableCredit").value(25))
                .andExpect(jsonPath("$.totalToppedUpCredit").value(50));
        mvc.perform(get(USERS_URL + "/{id}/wallet", binhId).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCredit").value(0))
                .andExpect(jsonPath("$.totalToppedUpCredit").value(0));
    }

    @Test
    void userWallet_whenUserMissing_returns404() throws Exception {
        mvc.perform(get(USERS_URL + "/{id}/wallet", 999_999_999L).header("Authorization", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void users_rejectNormalUserAndAnonymous() throws Exception {
        String userToken = "Bearer " + jwtService.issue(anId, false);
        mvc.perform(get(USERS_URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(USERS_URL).header("Authorization", userToken)).andExpect(status().isForbidden());
        mvc.perform(get(USERS_URL + "/{id}/wallet", anId).header("Authorization", userToken))
                .andExpect(status().isForbidden());
    }

    private long insertUser(String email, String fullName, String phone, int balance, int held, boolean admin) {
        return jdbc.sql(
                        "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit,"
                                + " is_admin) VALUES (:email, 'unused', :fullName, :phone, 'ACTIVE', :balance, :held,"
                                + " :admin) RETURNING id")
                .param("email", email)
                .param("fullName", fullName)
                .param("phone", phone)
                .param("balance", balance)
                .param("held", held)
                .param("admin", admin)
                .query(Long.class)
                .single();
    }

    private void insertOrder(long userId, int credits, String status) {
        jdbc.sql("INSERT INTO payment_orders (user_id, order_code, amount_vnd, credits, vnd_per_credit, provider,"
                        + " status, signature_verified, paid_at) VALUES (:userId, :code, :amount, :credits, 1000,"
                        + " 'MOCK', :status, TRUE, CASE WHEN :status = 'SUCCESS' THEN now() ELSE NULL END)")
                .param("userId", userId)
                .param("code", UUID.randomUUID().toString().replace("-", "").substring(0, 20))
                .param("amount", credits * 1000L)
                .param("credits", credits)
                .param("status", status)
                .update();
    }
}
