package com.shareloop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class AdminCategoryIT extends IntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcClient jdbc;

    private long adminId;

    // Thao tác admin giờ ghi activity_logs, mà actor_id có khoá ngoại tới users nên admin phải có thật trong DB.
    @BeforeEach
    void createAdmin() {
        adminId = jdbc.sql(
                        "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance) "
                                + "VALUES ('catalog-admin-2@test.local', 'unused', 'Admin', '0900000002', 'ACTIVE', 0) RETURNING id")
                .query(Long.class)
                .single();
    }

    @Test
    void adminCreatesReordersEditsAndHidesCategory() throws Exception {
        String token = jwtService.issue(adminId, true);
        mvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Admin Test Category", null, false, true, 50)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));
        long id = categoryId("Admin Test Category");

        mvc.perform(put("/api/v1/admin/categories/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Admin Renamed Category", null, true, false, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restricted").value(true))
                .andExpect(jsonPath("$.boostable").value(false))
                .andExpect(jsonPath("$.sortOrder").value(0));

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Admin Renamed Category"));
        mvc.perform(patch("/api/v1/admin/categories/{id}/active", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/categories/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Admin Renamed Category")));

        assertThat(jdbc.sql("SELECT is_deleted FROM item_categories WHERE id = :id")
                        .param("id", id)
                        .query(Boolean.class)
                        .single())
                .isFalse();
    }

    @Test
    void hidingParentHidesChildrenAndRejectsCycles() throws Exception {
        String token = jwtService.issue(adminId, true);
        mvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Admin Parent", null, false, true, 30)))
                .andExpect(status().isCreated());
        long parentId = categoryId("Admin Parent");
        mvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Admin Child", parentId, false, true, 31)))
                .andExpect(status().isCreated());
        long childId = categoryId("Admin Child");

        mvc.perform(put("/api/v1/admin/categories/{id}", parentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Admin Parent", childId, false, true, 30)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_PARENT"))
                .andExpect(jsonPath("$.field").value("parentId"));

        mvc.perform(patch("/api/v1/admin/categories/{id}/active", parentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", not(hasItem("Admin Parent"))))
                .andExpect(jsonPath("$[*].name", not(hasItem("Admin Child"))));
        assertThat(jdbc.sql("SELECT is_active FROM item_categories WHERE id = :id")
                        .param("id", childId)
                        .query(Boolean.class)
                        .single())
                .isFalse();
    }

    @Test
    void adminEndpointsRejectNonAdminAndInvalidBody() throws Exception {
        String userToken = jwtService.issue(2L, false);
        String adminToken = jwtService.issue(adminId, true);
        mvc.perform(get("/api/v1/admin/categories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/categories").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Forbidden", null, false, true, 1)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  ", null, false, true, 1)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Orphan", 999999L, false, true, 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_PARENT"));
    }

    private String body(String name, Long parentId, boolean restricted, boolean boostable, int sortOrder) {
        return """
                {"name":"%s","parentId":%s,"restricted":%s,"boostable":%s,"sortOrder":%d}
                """.formatted(name, parentId, restricted, boostable, sortOrder);
    }

    private long categoryId(String name) {
        return jdbc.sql("SELECT id FROM item_categories WHERE name = :name")
                .param("name", name)
                .query(Long.class)
                .single();
    }
}
