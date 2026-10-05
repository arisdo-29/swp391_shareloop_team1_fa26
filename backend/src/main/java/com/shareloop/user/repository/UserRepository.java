package com.shareloop.user.repository;

import com.shareloop.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    // Derived query: email đã lưu chữ thường nên so khớp thẳng. Tài khoản xoá mềm bị @SQLRestriction loại.
    Optional<User> findByEmail(String email);
}
