package com.shareloop.listingfee;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /listing-fees đọc số từ cấu hình hệ thống: Admin đổi boost_fee qua API admin thì lần gọi sau thấy số mới. Chạy
 * trên PostgreSQL thật; giá trị cấu hình cũ được trả lại trong @AfterEach để không ảnh hưởng test khác.
 */
@AutoConfigureMockMvc
class ListingFeeConfigApiIT extends IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    ConfigService configService;

    @Autowired
    JdbcTemplate jdbc;

    private long adminUserId;
    private String originalBoostFee;

    // activity_logs.actor_id tham chiếu users nên Admin trong test phải là một user có thật.
    @BeforeEach
    void setUp() {
        adminUserId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status)"
                        + " VALUES (?, 'not-a-real-hash', 'Fee Admin IT', '0988000001', 'ACTIVE') RETURNING id",
                Long.class,
                "fee-admin-it-" + System.nanoTime() + "@test.local");
        originalBoostFee = configService.get(ConfigKey.BOOST_FEE);
    }

    @AfterEach
    void cleanUp() {
        configService.update(ConfigKey.BOOST_FEE, originalBoostFee, "Trả lại giá trị sau test", adminUserId);
        jdbc.update("DELETE FROM activity_logs WHERE actor_id = ?", adminUserId);
        jdbc.update("DELETE FROM users WHERE id = ?", adminUserId);
    }

    @Test
    void getListingFees_afterAdminChangesBoostFee_returnsNewBoostFee() throws Exception {
        // given: Admin đổi boost_fee thành một số khác giá trị đang dùng
        int newBoostFee = Integer.parseInt(originalBoostFee) + 7;
        String adminToken = jwtService.issue(adminUserId, true);
        mockMvc.perform(put("/api/v1/admin/configs/{key}", ConfigKey.BOOST_FEE.key())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\": \"" + newBoostFee + "\", \"reason\": \"Test đổi phí đẩy bài\"}"))
                .andExpect(status().isOk());
        String userToken = jwtService.issue(adminUserId, false);

        // when + then: số mới hiện ra, các số khác vẫn đọc từ cấu hình, bài GIVE luôn 0
        mockMvc.perform(get("/api/v1/listing-fees").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boostFee").value(newBoostFee))
                .andExpect(jsonPath("$.postFeeSwap").value(configService.getInt(ConfigKey.POST_FEE)))
                .andExpect(jsonPath("$.boostDays").value(configService.getInt(ConfigKey.BOOST_DAYS)))
                .andExpect(jsonPath("$.postFeeGive").value(0));
    }

    @Test
    void getListingFees_withoutToken_returnsUnauthorized() throws Exception {
        // given: không gửi token

        // when + then
        mockMvc.perform(get("/api/v1/listing-fees")).andExpect(status().isUnauthorized());
    }
}
