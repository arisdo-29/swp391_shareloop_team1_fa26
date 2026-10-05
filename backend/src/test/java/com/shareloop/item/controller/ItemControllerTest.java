package com.shareloop.item.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.config.ClockConfig;
import com.shareloop.config.JwtConfig;
import com.shareloop.config.SecurityConfig;
import com.shareloop.config.WebConfig;
import com.shareloop.item.dto.CreateItemRequest;
import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.service.ItemService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ItemController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, WebConfig.class})
@ActiveProfiles("test")
class ItemControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    ItemService itemService;

    @Test
    @DisplayName("Tạo bài không có token trả về 401 Unauthorized")
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Tạo bài có JWT hợp lệ trả về 201 Created")
    void create_withToken_returns201() throws Exception {
        String token = jwtService.issue(10L, false);

        ItemResponse response = new ItemResponse(
                1L,
                10L,
                1L,
                1L,
                OfferType.GIVE,
                "Sách giáo trình",
                "Mô tả sách",
                null,
                ItemCondition.NEW,
                null,
                "NXB",
                ItemStatus.PENDING_REVIEW,
                Instant.now(),
                Instant.now());

        when(itemService.createItem(eq(10L), any(CreateItemRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "categoryId": 1,
                          "areaId": 1,
                          "offerType": "GIVE",
                          "title": "Sách giáo trình",
                          "description": "Mô tả sách",
                          "condition": "NEW"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.donorId").value(10))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    @DisplayName("Xem chi tiết bài đăng không cần token trả về 200 OK")
    void getById_public_returns200() throws Exception {
        ItemResponse response = new ItemResponse(
                1L,
                10L,
                1L,
                1L,
                OfferType.GIVE,
                "Sách",
                "Mô tả",
                null,
                ItemCondition.NEW,
                null,
                null,
                ItemStatus.APPROVED,
                Instant.now(),
                Instant.now());

        when(itemService.getItem(eq(1L), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Sách"));
    }

    @Test
    @DisplayName("Xem danh sách bài đăng của tôi không có token trả về 401 Unauthorized")
    void listMyItems_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/me/items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Xem danh sách bài đăng của tôi có token trả về 200 OK")
    void listMyItems_withToken_returns200() throws Exception {
        String token = jwtService.issue(10L, false);

        ItemResponse item = new ItemResponse(
                1L,
                10L,
                1L,
                1L,
                OfferType.GIVE,
                "Sách của tôi",
                "Mô tả",
                null,
                ItemCondition.NEW,
                null,
                null,
                ItemStatus.PENDING_REVIEW,
                Instant.now(),
                Instant.now());

        when(itemService.listMyItems(10L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/me/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Sách của tôi"));
    }
}
