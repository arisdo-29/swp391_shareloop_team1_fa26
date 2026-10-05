package com.shareloop.config;

import com.shareloop.common.api.ErrorResponse;
import com.shareloop.common.exception.CommonErrorCode;
import com.shareloop.common.exception.ErrorCode;
import com.shareloop.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    /** Các GET công khai; thêm đường dẫn mới chỉ sửa ở đây. */
    static final String[] PUBLIC_GET = {"/api/v1/categories/**", "/api/v1/areas/**", "/api/v1/items/*"};

    private static final String[] PERMIT_ALL = {
        "/api/v1/auth/**", "/actuator/health", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(PERMIT_ALL)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET)
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                                .authenticationEntryPoint(authenticationEntryPoint)
                                .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /** Lỗi ném ở tầng filter không qua @RestControllerAdvice, nên tự ghi ErrorResponse tại đây. */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, ex) -> writeError(objectMapper, response, CommonErrorCode.UNAUTHORIZED);
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, ex) -> writeError(objectMapper, response, CommonErrorCode.FORBIDDEN);
    }

    private static void writeError(ObjectMapper objectMapper, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        var body = new ErrorResponse(
                errorCode.code(), errorCode.defaultMessage(), null, null, MDC.get(TraceIdFilter.MDC_KEY));
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(
                allowedOrigins.stream().filter(o -> !o.isBlank()).toList());
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", TraceIdFilter.HEADER));
        config.setExposedHeaders(List.of(TraceIdFilter.HEADER));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
