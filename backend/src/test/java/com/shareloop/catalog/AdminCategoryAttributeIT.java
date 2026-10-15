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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class AdminCategoryAttributeIT extends IntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcClient jdbc;

    @Test
    void adminCreatesUpdatesAndHidesAttributeFromPublicForm() throws Exception {
        long category = categoryId("Sách, truyện, giáo trình, văn phòng phẩm");
        String token = jwtService.issue(1L, true);
        String path = "/api/v1/admin/categories/{category}/attributes";
        mvc.perform(post(path, category)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Màu sắc", "SELECT", "[\"Đỏ\",\"Xanh\"]", false, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.options[0]").value("Đỏ"));
        long attribute = attributeId(category, "Màu sắc");
        mvc.perform(get("/api/v1/categories/{category}/attributes", category))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Màu sắc")));
        mvc.perform(put(path + "/{attribute}", category, attribute)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Màu", "SELECT", "[\"Đỏ\",\"Xanh\"]", true, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.required").value(true));
        mvc.perform(patch(path + "/{attribute}/active", category, attribute)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/categories/{category}/attributes", category))
                .andExpect(jsonPath("$[*].name", not(hasItem("Màu"))));
        mvc.perform(get(path, category).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[*].name", hasItem("Màu")));
        assertThat(jdbc.sql("SELECT is_deleted FROM item_attributes WHERE id = :id")
                        .param("id", attribute)
                        .query(Boolean.class)
                        .single())
                .isFalse();
    }

    @Test
    void invalidOptionsAndProtectedOperatingStatusAreRejected() throws Exception {
        long books = categoryId("Sách, truyện, giáo trình, văn phòng phẩm");
        long electronics = categoryId("Điện tử và phụ kiện");
        long operatingStatus = attributeId(electronics, "Trạng thái hoạt động");
        String token = jwtService.issue(1L, true);
        mvc.perform(post("/api/v1/admin/categories/{category}/attributes", books)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Màu", "SELECT", "[]", false, 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_ATTRIBUTE_OPTIONS"));
        mvc.perform(patch(
                                "/api/v1/admin/categories/{category}/attributes/{attribute}/active",
                                electronics,
                                operatingStatus)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_REQUIRED_ATTRIBUTE"));
        mvc.perform(put("/api/v1/admin/categories/{category}/attributes/{attribute}", electronics, operatingStatus)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Trạng thái hoạt động", "SELECT", "[\"NORMAL\"]", false, 1)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/categories/{category}/attributes", electronics))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Trạng thái hoạt động')].required")
                        .value(true));
    }

    @Test
    void adminAttributeRejectsNonAdminAndWrongCategory() throws Exception {
        long books = categoryId("Sách, truyện, giáo trình, văn phòng phẩm");
        long electronics = categoryId("Điện tử và phụ kiện");
        long operatingStatus = attributeId(electronics, "Trạng thái hoạt động");
        String user = jwtService.issue(2L, false);
        String admin = jwtService.issue(1L, true);
        mvc.perform(get("/api/v1/admin/categories/{category}/attributes", books)
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/categories/{category}/attributes/{attribute}", books, operatingStatus)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test", "TEXT", "null", false, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATALOG_ATTRIBUTE_NOT_FOUND"));
    }

    private String body(String name, String type, String options, boolean required, int sortOrder) {
        return "{\"name\":\"%s\",\"dataType\":\"%s\",\"options\":%s,\"required\":%s,\"sortOrder\":%d}"
                .formatted(name, type, options, required, sortOrder);
    }

    private long categoryId(String name) {
        return jdbc.sql("SELECT id FROM item_categories WHERE name = :name")
                .param("name", name)
                .query(Long.class)
                .single();
    }

    private long attributeId(long category, String name) {
        return jdbc.sql("SELECT id FROM item_attributes WHERE category_id = :category AND name = :name")
                .param("category", category)
                .param("name", name)
                .query(Long.class)
                .single();
    }
}
