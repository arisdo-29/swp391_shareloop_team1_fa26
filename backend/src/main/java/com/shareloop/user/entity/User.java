package com.shareloop.user.entity;

import com.shareloop.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
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

    // Chỉ lưu id khu vực (bảng areas thuộc module catalog), không @ManyToOne sang entity của module khác.
    @Column(name = "area_id")
    private Long areaId;

    // Chỉ lưu id ảnh (bảng media_files thuộc module media); null khi chưa có ảnh đại diện.
    @Column(name = "avatar_media_id")
    private Long avatarMediaId;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    // STRING: lưu tên enum ("ACTIVE") vào DB, khớp CHECK của cột; ORDINAL sẽ lưu số và dễ sai khi đổi thứ tự.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(name = "otp_code_hash", length = 100)
    private String otpCodeHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "otp_purpose", length = 20)
    private OtpPurpose otpPurpose;

    @Column(name = "otp_expires_at")
    private Instant otpExpiresAt;

    // Cột là SMALLINT nên khai short; kiểu int sẽ làm ddl-auto: validate báo lệch kiểu.
    @Column(name = "otp_failed_count", nullable = false)
    private short otpFailedCount;

    @Column(name = "registration_ip", length = 45)
    private String registrationIp;

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

    public Long getAreaId() {
        return areaId;
    }

    public Long getAvatarMediaId() {
        return avatarMediaId;
    }

    public boolean isAdmin() {
        return admin;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getOtpCodeHash() {
        return otpCodeHash;
    }

    public OtpPurpose getOtpPurpose() {
        return otpPurpose;
    }

    public Instant getOtpExpiresAt() {
        return otpExpiresAt;
    }

    public int getOtpFailedCount() {
        return otpFailedCount;
    }

    public String getRegistrationIp() {
        return registrationIp;
    }

    /** Lưu mã OTP mới (bản băm) và đặt lại số lần nhập sai về 0. */
    public void startOtp(String codeHash, OtpPurpose purpose, Instant expiresAt) {
        this.otpCodeHash = codeHash;
        this.otpPurpose = purpose;
        this.otpExpiresAt = expiresAt;
        this.otpFailedCount = 0;
    }

    public void increaseOtpFailedCount() {
        this.otpFailedCount++;
    }

    /** Xoá toàn bộ cột otp_*: dùng khi nhập đúng mã hoặc khi sai quá số lần cho phép. */
    public void clearOtp() {
        this.otpCodeHash = null;
        this.otpPurpose = null;
        this.otpExpiresAt = null;
        this.otpFailedCount = 0;
    }

    /** Đăng ký lại khi tài khoản còn PENDING_VERIFICATION: ghi đè thông tin theo request mới. */
    public void updatePendingRegistration(String passwordHash, String fullName, String phone) {
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
    }

    /** Xác thực email xong. Không đặt tên activate() vì BaseEntity.activate() là bật is_active. */
    public void markVerified() {
        this.status = UserStatus.ACTIVE;
    }

    public void recordRegistrationIp(String registrationIp) {
        this.registrationIp = registrationIp;
    }

    /** Cập nhật phần hồ sơ người dùng được tự sửa (FR-06). Email và số điện thoại cố ý không có ở đây. */
    public void updateProfile(String fullName, Long areaId, Long avatarMediaId) {
        this.fullName = fullName;
        this.areaId = areaId;
        this.avatarMediaId = avatarMediaId;
    }

    /** Đổi mật khẩu (FR-08). Chỉ nhận bản băm BCrypt, không bao giờ nhận mật khẩu gốc. */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
