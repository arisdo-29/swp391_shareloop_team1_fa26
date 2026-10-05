package com.shareloop.catalog.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.catalog.dto.AreaResponse;
import com.shareloop.catalog.service.AreaService;
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

@WebMvcTest(AreaController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@ActiveProfiles("test")
class AreaControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AreaService areaService;

    @Test
    void listReturnsTreeWithoutToken() throws Exception {
        var district = new AreaResponse(2L, "Quận 1", 1L, (short) 2, List.of());
        var city = new AreaResponse(1L, "TP. Hồ Chí Minh", null, (short) 1, List.of(district));
        when(areaService.listTree()).thenReturn(List.of(city));

        mockMvc.perform(get("/api/v1/areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("TP. Hồ Chí Minh"))
                .andExpect(jsonPath("$[0].children[0].name").value("Quận 1"))
                .andExpect(jsonPath("$[0].children[0].parentId").value(1))
                .andExpect(jsonPath("$[0].children[0].children").isEmpty());
    }
}
