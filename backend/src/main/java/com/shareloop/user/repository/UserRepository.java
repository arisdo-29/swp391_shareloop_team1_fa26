package com.shareloop.user.repository;

import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    // Derived query: email đã lưu chữ thường nên so khớp thẳng. Tài khoản xoá mềm bị @SQLRestriction loại.
    Optional<User> findByEmail(String email);

    // Số điện thoại chỉ tính trùng với tài khoản ACTIVE (khớp unique index ux_users_phone_active).
    boolean existsByPhoneAndStatus(String phone, UserStatus status);
}
