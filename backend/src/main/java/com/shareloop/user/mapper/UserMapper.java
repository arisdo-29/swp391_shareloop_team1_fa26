package com.shareloop.user.mapper;

import com.shareloop.auth.dto.AuthUserResponse;
import com.shareloop.user.dto.ContactInfo;
import com.shareloop.user.dto.MeResponse;
import com.shareloop.user.entity.User;
import org.springframework.stereotype.Component;

/** Entity sang DTO, viết tay (không dùng MapStruct). */
@Component
public class UserMapper {

    // Cùng dạng đường dẫn MediaService trả khi upload (docs/api/media.md: GET /api/v1/media/{id}).
    private static final String MEDIA_URL_PREFIX = "/api/v1/media/";

    public AuthUserResponse toAuthUserResponse(User user) {
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.isAdmin());
    }

    public MeResponse toMeResponse(User user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.isAdmin(),
                user.getAreaId(),
                user.getAvatarMediaId(),
                buildAvatarUrl(user.getAvatarMediaId()));
    }

    // Chỉ 4 trường này được trao cho đối tác (BR-T11); không thêm trường khác vào ContactInfo.
    public ContactInfo toContactInfo(User user) {
        return new ContactInfo(user.getId(), user.getFullName(), user.getEmail(), user.getPhone());
    }

    private String buildAvatarUrl(Long avatarMediaId) {
        if (avatarMediaId == null) {
            return null;
        }
        return MEDIA_URL_PREFIX + avatarMediaId;
    }
}
