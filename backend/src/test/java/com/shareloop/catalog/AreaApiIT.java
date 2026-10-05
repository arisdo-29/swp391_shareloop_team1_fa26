package com.shareloop.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Kiểm tra migration danh mục/khu vực và endpoint trên PostgreSQL thật. */
@AutoConfigureMockMvc
class AreaApiIT extends IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void seededAreasAreReturnedAsPublicTwoLevelTree() throws Exception {
        mockMvc.perform(get("/api/v1/areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("TP. Hồ Chí Minh"))
                .andExpect(jsonPath("$[0].level").value(1))
                .andExpect(jsonPath("$[0].children.length()").value(5))
                .andExpect(jsonPath("$[0].children[0].name").value("Quận 1"))
                .andExpect(jsonPath("$[0].children[0].level").value(2));
    }
}
