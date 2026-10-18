package com.shareloop.user.repository;

import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    // Derived query: email đã lưu chữ thường nên so khớp thẳng. Tài khoản xoá mềm bị @SQLRestriction loại.
    Optional<User> findByEmail(String email);

    // Số điện thoại chỉ tính trùng với tài khoản ACTIVE (khớp unique index ux_users_phone_active).
    boolean existsByPhoneAndStatus(String phone, UserStatus status);

    // Số tài khoản tạo từ một IP sau mốc since (FR-10). Tài khoản đã xoá mềm (ví dụ bị job dọn PENDING) không tính.
    long countByRegistrationIpAndCreatedAtAfter(String registrationIp, Instant since);

    /**
     * Id các tài khoản đủ điều kiện để job dọn tài khoản chưa xác thực xoá mềm (FR-117, BR-U06): đúng status, tạo
     * trước createdBefore, và không có OTP còn hạn tại now. Tài khoản đã xoá mềm bị @SQLRestriction loại sẵn.
     * Phân trang keyset (id > afterId, tăng dần) để một dòng lỗi không bị lấy lại mãi trong cùng một lượt.
     */
    @Query("select u.id from User u"
            + " where u.status = :status and u.createdAt < :createdBefore"
            + " and (u.otpExpiresAt is null or u.otpExpiresAt <= :now)"
            + " and u.id > :afterId order by u.id")
    List<Long> findCleanupCandidateIds(
            @Param("status") UserStatus status,
            @Param("createdBefore") Instant createdBefore,
            @Param("now") Instant now,
            @Param("afterId") long afterId,
            Limit limit);

    /**
     * Danh sách ID của các quản trị viên đang hoạt động (dùng cho gửi thông báo cảnh báo hệ thống).
     */
    @Query("select u.id from User u where u.admin = true and u.isActive = true")
    List<Long> findAdminIds();
}
