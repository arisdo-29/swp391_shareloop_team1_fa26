package com.shareloop.catalog;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Mẫu integration test: DB PostgreSQL thật (Testcontainers), gọi API thật qua MockMvc. */
@AutoConfigureMockMvc
class CategoryApiIT extends IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    long activeId;
    long inactiveId;
    long deletedId;

    @BeforeEach
    void insertCategories() {
        // Không @Transactional: dữ liệu commit thật, nên tự dọn trong @AfterEach.
        activeId = insert("Active IT", true, false, 2);
        inactiveId = insert("Inactive IT", false, false, 1);
        deletedId = insert("Deleted IT", true, true, 3);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM item_categories WHERE name LIKE '% IT'");
    }

    @Test
    void listReturnsOnlyActiveNotDeletedCategories() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Active IT")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Inactive IT"))))
                .andExpect(jsonPath("$[*].name", not(hasItem("Deleted IT"))));
    }

    @Test
    void getReturnsActiveCategory() throws Exception {
        mockMvc.perform(get("/api/v1/categories/" + activeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Active IT"))
                .andExpect(jsonPath("$.sortOrder").value(2));
    }

    @Test
    void getReturns404ForInactiveDeletedAndUnknownIds() throws Exception {
        for (long id : new long[] {inactiveId, deletedId, 999_999L}) {
            mockMvc.perform(get("/api/v1/categories/" + id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CATALOG_CATEGORY_NOT_FOUND"));
        }
    }

    private long insert(String name, boolean active, boolean deleted, int sortOrder) {
        return jdbc.queryForObject(
                "INSERT INTO item_categories (name, sort_order, is_active, is_deleted) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class,
                name,
                sortOrder,
                active,
                deleted);
    }
}
