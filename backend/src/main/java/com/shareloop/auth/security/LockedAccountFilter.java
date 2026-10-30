package com.shareloop.auth.security;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogQueryService;
import com.shareloop.auth.AuthErrorCode;
import com.shareloop.common.api.ErrorResponse;
import com.shareloop.common.web.TraceIdFilter;
import com.shareloop.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Chặn token đã cấp của tài khoản bị khoá (FR-107). JWT không lưu trạng thái nên token cấp trước khi khoá vẫn hợp lệ
 * tới lúc hết hạn; filter này kiểm lại tài khoản ở mỗi request có token và trả 403 AUTH_ACCOUNT_LOCKED.
 *
 * <p>Không gắn @Component: filter được đăng ký trong {@link LockedAccountFilterConfig}, để các test @WebMvcTest không
 * tự nạp filter này (chúng không có UserService thật).
 */
public class LockedAccountFilter extends OncePerRequestFilter {

    private final UserService userService;
    private final ActivityLogQueryService activityLogQueryService;
    private final ObjectMapper objectMapper;

    public LockedAccountFilter(UserService userService, ObjectMapper objectMapper) {
        this(userService, null, objectMapper);
    }

    public LockedAccountFilter(
            UserService userService, ActivityLogQueryService activityLogQueryService, ObjectMapper objectMapper) {
        this.userService = userService;
        this.activityLogQueryService = activityLogQueryService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long userId = findUserIdFromToken();
        if (userId != null && userService.isLocked(userId)) {
            writeLockedError(response, userId);
            return; // dừng tại đây, không cho request đi tiếp vào controller
        }
        chain.doFilter(request, response);
    }

    // Filter chạy sau Spring Security nên token (nếu có) đã được kiểm chữ ký và hạn; ở đây chỉ đọc claim "sub".
    // Không có token hoặc sub không phải số thì trả null: để Spring Security và CurrentUserArgumentResolver xử lý.
    private Long findUserIdFromToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Lỗi ở tầng filter không qua @RestControllerAdvice, nên tự ghi ErrorResponse như SecurityConfig.writeError.
    private void writeLockedError(HttpServletResponse response, Long userId) throws IOException {
        AuthErrorCode errorCode = AuthErrorCode.AUTH_ACCOUNT_LOCKED;
        String reason = activityLogQueryService != null
                ? activityLogQueryService
                        .findLatestReason(ActivityType.USER_LOCK.name(), userId)
                        .map(String::trim)
                        .filter(r -> !r.isEmpty())
                        .orElse(null)
                : null;
        String message = reason == null ? errorCode.defaultMessage() : "Tài khoản đã bị khoá. Lý do: " + reason;
        Map<String, Object> details = reason == null ? null : Map.of("reason", reason);
        var body = new ErrorResponse(errorCode.code(), message, null, details, MDC.get(TraceIdFilter.MDC_KEY));
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
