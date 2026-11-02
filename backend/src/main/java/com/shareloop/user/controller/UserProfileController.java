package com.shareloop.user.controller;

import com.shareloop.user.dto.PublicProfileResponse;
import com.shareloop.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hồ sơ công khai của người dùng khác. Cần token: SecurityConfig yêu cầu xác thực cho mọi đường dẫn không công khai,
 * và /api/v1/users/** không nằm trong danh sách công khai.
 */
@Tag(name = "User profile", description = "Hồ sơ công khai của người dùng")
@RestController
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @Operation(summary = "Xem hồ sơ công khai của một người dùng")
    @GetMapping("/api/v1/users/{id}/profile")
    public PublicProfileResponse getProfile(@PathVariable long id) {
        return userProfileService.getPublicProfile(id);
    }
}
