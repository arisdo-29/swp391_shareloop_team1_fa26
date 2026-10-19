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
class AdminAreaIT extends IntegrationTest {
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
                                + "VALUES ('catalog-admin-1@test.local', 'unused', 'Admin', '0900000001', 'ACTIVE', 0) RETURNING id")
                .query(Long.class)
                .single();
    }

    @Test
    void adminMaintainsTwoLevelTreeAndHidingParentHidesChildren() throws Exception {
        String token = jwtService.issue(adminId, true);
        mvc.perform(post("/api/v1/admin/areas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Admin Test City", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.level").value(1));
        long parent = areaId("Admin Test City");
        mvc.perform(post("/api/v1/admin/areas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Admin Test District", parent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.level").value(2));
        long child = areaId("Admin Test District");

        mvc.perform(get("/api/v1/areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Admin Test City")));
        mvc.perform(put("/api/v1/admin/areas/{id}", child)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Renamed District", parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed District"));

        mvc.perform(put("/api/v1/admin/areas/{id}", parent)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Admin Test City", child)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_AREA_PARENT"));
        long existingCity = areaId("TP. Hồ Chí Minh");
        mvc.perform(put("/api/v1/admin/areas/{id}", parent)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Admin Test City", existingCity)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_AREA_HAS_CHILDREN"));
        mvc.perform(patch("/api/v1/admin/areas/{id}/active", parent)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
        assertThat(active(child)).isFalse();
        mvc.perform(get("/api/v1/areas")).andExpect(jsonPath("$[*].name", not(hasItem("Admin Test City"))));
        mvc.perform(patch("/api/v1/admin/areas/{id}/active", child)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patch("/api/v1/admin/areas/{id}/active", parent)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk());
        assertThat(active(child)).isFalse();
    }

    @Test
    void adminAreaRejectsInvalidParentAndNonAdmin() throws Exception {
        String admin = jwtService.issue(adminId, true);
        String user = jwtService.issue(2L, false);
        mvc.perform(post("/api/v1/admin/areas")
                        .header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Forbidden", null)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/areas")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Orphan", 999999L)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("parentId"));
        mvc.perform(post("/api/v1/admin/areas")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  ", null)))
                .andExpect(status().isBadRequest());
    }

    private String body(String name, Long parentId) {
        return "{\"name\":\"%s\",\"parentId\":%s}".formatted(name, parentId);
    }

    private long areaId(String name) {
        return jdbc.sql("SELECT id FROM areas WHERE name = :name")
                .param("name", name)
                .query(Long.class)
                .single();
    }

    private boolean active(long id) {
        return jdbc.sql("SELECT is_active FROM areas WHERE id = :id")
                .param("id", id)
                .query(Boolean.class)
                .single();
    }
}
