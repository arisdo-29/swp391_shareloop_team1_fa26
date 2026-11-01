package com.shareloop.auth.security;

import com.shareloop.audit.service.ActivityLogQueryService;
import com.shareloop.user.service.UserService;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Đăng ký {@link LockedAccountFilter} vào chuỗi filter của servlet, ngay sau Spring Security. */
@Configuration
public class LockedAccountFilterConfig {

    // Spring Security chạy ở thứ tự -100; +1 để filter này chạy ngay sau, lúc SecurityContext đã có người dùng.
    static final int ORDER_AFTER_SPRING_SECURITY = SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1;

    // FilterRegistrationBean: cách Spring Boot gắn một Filter vào servlet kèm thứ tự chạy, thay cho @Component.
    @Bean
    public FilterRegistrationBean<LockedAccountFilter> lockedAccountFilter(
            UserService userService, ActivityLogQueryService activityLogQueryService, ObjectMapper objectMapper) {
        var registration = new FilterRegistrationBean<>(
                new LockedAccountFilter(userService, activityLogQueryService, objectMapper));
        registration.setOrder(ORDER_AFTER_SPRING_SECURITY);
        return registration;
    }
}
