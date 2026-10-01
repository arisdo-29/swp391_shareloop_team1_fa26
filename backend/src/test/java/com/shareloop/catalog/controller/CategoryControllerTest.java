package com.shareloop.catalog.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.catalog.CatalogErrorCode;
import com.shareloop.catalog.dto.CategoryResponse;
import com.shareloop.catalog.service.CategoryService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Mẫu test controller: chỉ dựng tầng web, service bị thay bằng mock. Cần import SecurityConfig (slice không tự nạp
 * @Configuration) và JwtConfig (SecurityConfig dùng converter); profile test cấp app.jwt.secret.
 */
@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@ActiveProfiles("test")
class CategoryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CategoryService categoryService;

    @Test
    void listReturns200WithoutToken() throws Exception {
        when(categoryService.listActive()).thenReturn(List.of(new CategoryResponse(1L, "Sách", null, false, true, 1)));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Sách"))
                .andExpect(jsonPath("$[0].boostable").value(true));
    }

    @Test
    void getReturns200() throws Exception {
        when(categoryService.getById(1L)).thenReturn(new CategoryResponse(1L, "Sách", null, false, true, 1));

        mockMvc.perform(get("/api/v1/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sách"));
    }

    @Test
    void getReturns404InErrorResponseFormat() throws Exception {
        when(categoryService.getById(99L))
                .thenThrow(new BusinessException(CatalogErrorCode.CATALOG_CATEGORY_NOT_FOUND));

        mockMvc.perform(get("/api/v1/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATALOG_CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
