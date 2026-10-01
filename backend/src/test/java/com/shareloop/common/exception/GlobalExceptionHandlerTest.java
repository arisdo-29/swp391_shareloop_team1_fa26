package com.shareloop.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.common.web.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void validationFailureReturns400WithFirstField() throws Exception {
        mockMvc.perform(post("/fake/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("name"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/fake/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void pathVariableTypeMismatchReturns400WithParameterName() throws Exception {
        mockMvc.perform(get("/fake/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("id"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void businessNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/fake/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.field").doesNotExist());
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        mockMvc.perform(get("/fake/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void businessRuleReturns422WithFieldAndDetails() throws Exception {
        mockMvc.perform(get("/fake/rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATED"))
                .andExpect(jsonPath("$.message").value("Mô tả có số điện thoại."))
                .andExpect(jsonPath("$.field").value("description"))
                .andExpect(jsonPath("$.details.match").value("09xx xxx xxx"));
    }

    @Test
    void unexpectedErrorReturns500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/fake/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("at com."))))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void traceIdInBodyMatchesResponseHeader() throws Exception {
        mockMvc.perform(get("/fake/boom").header(TraceIdFilter.HEADER, "abc123"))
                .andExpect(header().string(TraceIdFilter.HEADER, "abc123"))
                .andExpect(jsonPath("$.traceId").value("abc123"));
    }

    record FakeBody(@NotBlank String name) {}

    @RestController
    static class FakeController {

        @PostMapping("/fake/validate")
        String validate(@Valid @RequestBody FakeBody body) {
            return "ok";
        }

        @GetMapping("/fake/items/{id}")
        String item(@PathVariable Long id) {
            return "ok";
        }

        @GetMapping("/fake/not-found")
        String notFound() {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }

        @GetMapping("/fake/rule")
        String rule() {
            throw new BusinessException(
                    CommonErrorCode.BUSINESS_RULE_VIOLATED,
                    "Mô tả có số điện thoại.",
                    "description",
                    Map.of("match", "09xx xxx xxx"));
        }

        @GetMapping("/fake/boom")
        String boom() {
            throw new IllegalStateException("secret SQL: select * from users");
        }
    }
}
