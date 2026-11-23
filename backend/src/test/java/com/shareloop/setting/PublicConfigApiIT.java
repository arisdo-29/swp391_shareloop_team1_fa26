package com.shareloop.setting;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** G1: endpoint cấu hình công khai chỉ mở đúng một đường, chỉ trả các khoá chọn lọc và phản ánh giá trị cấu hình. */
@AutoConfigureMockMvc
@Transactional
class PublicConfigApiIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void publicConfigs_withoutLogin_returnsSelectedNumbersOnly() throws Exception {
        // when, then
        mvc.perform(get("/api/v1/configs/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_pending_days").value(3))
                .andExpect(jsonPath("$.chat_open_days").value(10))
                // khoá nội bộ không bị lộ
                .andExpect(jsonPath("$", not(hasKey("post_fee"))))
                .andExpect(jsonPath("$", not(hasKey("vnd_per_credit"))))
                .andExpect(jsonPath("$", not(hasKey("report_serious_reasons"))));
    }

    @Test
    void publicConfigs_afterAdminChangesValue_returnsNewNumber() throws Exception {
        // given
        jdbc.update(
                "UPDATE website_attributes SET attr_value = '4' WHERE attr_group = 'CONFIG' AND attr_key = 'request_pending_days'");

        // when, then
        mvc.perform(get("/api/v1/configs/public"))
                .andExpect(jsonPath("$.request_pending_days").value(4));
    }

    @Test
    void otherConfigPaths_stillRequireLogin() throws Exception {
        // when, then: chỉ đúng /configs/public được mở
        mvc.perform(get("/api/v1/configs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/configs/other")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/configs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }
}
