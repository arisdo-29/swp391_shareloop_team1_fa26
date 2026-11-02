package com.shareloop.user.mapper;

import com.shareloop.auth.dto.AuthUserResponse;
import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.user.dto.AdminAccountRole;
import com.shareloop.user.dto.AdminAccountState;
import com.shareloop.user.dto.AdminUserSummary;
import com.shareloop.user.dto.ContactInfo;
import com.shareloop.user.dto.MeResponse;
import com.shareloop.user.dto.PublicProfileResponse;
import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
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

    /**
     * Hồ sơ công khai. Số liệu uy tín và số giao dịch do service lấy từ module reputation, request rồi truyền vào; chỉ
     * lấy họ tên, ảnh, ngày tham gia từ User. Không thêm email hay số điện thoại vào đây.
     */
    public PublicProfileResponse toPublicProfile(
            User user, String district, ReputationSummary reputation, long completedTransactions) {
        return new PublicProfileResponse(
                user.getId(),
                user.getFullName(),
                buildAvatarUrl(user.getAvatarMediaId()),
                buildInitials(user.getFullName()),
                district,
                reputation.rank(),
                reputation.trustStars(),
                reputation.asDescribedRate(),
                completedTransactions,
                user.getCreatedAt());
    }

    // Lấy chữ đầu của từ đầu và từ cuối: "Nguyễn Văn An" thành "NA"; tên một từ thì lấy một chữ.
    private String buildInitials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        String[] words = fullName.trim().split("\\s+");
        String first = words[0].substring(0, 1);
        if (words.length == 1) {
            return first.toUpperCase();
        }
        String last = words[words.length - 1].substring(0, 1);
        return (first + last).toUpperCase();
    }

    public AdminUserSummary toAdminSummary(User user) {
        return new AdminUserSummary(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.isAdmin() ? AdminAccountRole.ADMIN : AdminAccountRole.USER,
                resolveState(user),
                !user.isActive(),
                user.getCreatedAt());
    }

    // Thứ tự ưu tiên phải khớp câu truy vấn lọc trong UserRepository.searchForAdmin.
    private AdminAccountState resolveState(User user) {
        if (user.getStatus() == UserStatus.BANNED) {
            return AdminAccountState.BANNED;
        }
        if (!user.isActive()) {
            return AdminAccountState.LOCKED;
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            return AdminAccountState.PENDING_VERIFICATION;
        }
        return AdminAccountState.ACTIVE;
    }

    /** Đường dẫn ảnh đại diện dạng /api/v1/media/{id}; null khi chưa có ảnh. */
    public String buildAvatarUrl(Long avatarMediaId) {
        if (avatarMediaId == null) {
            return null;
        }
        return MEDIA_URL_PREFIX + avatarMediaId;
    }
}
