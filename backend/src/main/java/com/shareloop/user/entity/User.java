package com.shareloop.user.entity;

import com.shareloop.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/**
 * Bảng users, lát cắt thông tin đăng nhập và hồ sơ cơ bản. Cột ví, AI, uy tín thuộc module khác nên không map ở đây
 * (một cột chỉ map ở một entity).
 */
@Entity
@Table(name = "users")
@SQLRestriction("is_deleted = false") // tài khoản xoá mềm tự bị loại khỏi mọi truy vấn
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "phone", nullable = false, length = 15)
    private String phone;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    // STRING: lưu tên enum ("ACTIVE") vào DB, khớp CHECK của cột; ORDINAL sẽ lưu số và dễ sai khi đổi thứ tự.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    protected User() {}

    public User(String email, String passwordHash, String fullName, String phone, boolean admin, UserStatus status) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.admin = admin;
        this.status = status;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isAdmin() {
        return admin;
    }

    public UserStatus getStatus() {
        return status;
    }
}
