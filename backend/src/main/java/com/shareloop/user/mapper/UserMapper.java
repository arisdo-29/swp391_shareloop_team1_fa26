package com.shareloop.user.mapper;

import com.shareloop.auth.dto.AuthUserResponse;
import com.shareloop.user.entity.User;
import org.springframework.stereotype.Component;

/** Entity sang DTO, viết tay (không dùng MapStruct). */
@Component
public class UserMapper {

    public AuthUserResponse toAuthUserResponse(User user) {
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.isAdmin());
    }
}
