package com.shareloop.review.dto;

import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.entity.OperatingStatus;
import com.shareloop.setting.dto.ChecklistItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Chi tiết bài đăng cho Admin kiểm duyệt, ở mọi trạng thái")
public record ReviewItemDetailResponse(
        @Schema(description = "ID bài đăng", example = "42") Long id,

        @Schema(description = "Tiêu đề bài đăng", example = "Áo khoác gió nam")
        String title,

        @Schema(description = "Mô tả món đồ") String description,

        @Schema(description = "Món đồ muốn đổi (chỉ bài SWAP, bài GIVE là null)", example = "Giày thể thao size 42")
        String desiredItem,

        @Schema(description = "Hình thức: GIVE (cho tặng) hoặc SWAP (trao đổi)", example = "SWAP")
        OfferType offerType,

        @Schema(description = "Tình trạng món đồ", example = "GOOD")
        ItemCondition condition,

        @Schema(description = "Mô tả lỗi (bài đồ lỗi), có thể null")
        String defectNote,

        @Schema(description = "Thương hiệu, có thể null", example = "Uniqlo")
        String brand,

        @Schema(description = "Trạng thái bài đăng", example = "PENDING_REVIEW")
        ItemStatus status,

        @Schema(description = "Bài có đang hoạt động hay bị ẩn (is_active)", example = "true")
        boolean isActive,

        @Schema(
                description = "Trạng thái hoạt động của món đồ (nếu danh mục yêu cầu), có thể null",
                example = "WORKING")
        OperatingStatus operatingStatus,

        @Schema(description = "Đường dẫn ảnh món đồ", example = "[\"/api/v1/media/101\"]")
        List<String> images,

        @Schema(description = "Đường dẫn ảnh chỗ lỗi", example = "[\"/api/v1/media/102\"]")
        List<String> defectImages,

        @Schema(description = "ID danh mục", example = "3") Long categoryId,

        @Schema(description = "Tên danh mục; null nếu danh mục đã bị xoá", example = "Quần áo")
        String categoryName,

        @Schema(description = "ID khu vực", example = "7") Long areaId,

        @Schema(description = "Tên khu vực; null nếu khu vực đã bị xoá", example = "Quận 1")
        String areaName,

        @Schema(description = "ID người đăng", example = "9")
        Long donorId,

        @Schema(description = "Tên người đăng; null nếu tài khoản đã bị xoá", example = "Nguyễn Văn A")
        String donorName,

        @Schema(description = "Số sao uy tín của người đăng (1-5)", example = "3")
        Short donorTrustStars,

        @Schema(description = "Thuộc tính động của bài (rỗng nếu không có)")
        List<ReviewItemAttribute> attributes,

        @Schema(description = "Mức độ rủi ro do AI đánh giá (UNCHECKED, LOW, MEDIUM, HIGH)", example = "LOW")
        String aiRisk,

        @Schema(description = "Cờ kiểm duyệt (REVIEW nếu nghi vấn vi phạm)", example = "REVIEW")
        String moderationFlag,

        @Schema(description = "Mã gốc lý do gắn cờ; null nếu không có cờ", example = "LEXICON_REVIEW")
        String flagReasonCode,

        @Schema(description = "Lý do gắn cờ bằng tiếng Việt", example = "Có từ khoá cần xem xét: zalo")
        String flagReason,

        @Schema(description = "Từ khoá đã khớp khi kiểm duyệt tầng 1 (rỗng nếu không có)", example = "[\"zalo\"]")
        List<String> matchedTerms,

        @Schema(
                description = "Trạng thái xét nhãn chính hãng (NONE, PENDING, VERIFIED, REJECTED, REVOKED)",
                example = "NONE")
        String authStatus,

        @Schema(description = "Checklist kiểm duyệt hiện hành")
        List<ChecklistItem> checklist,

        @Schema(description = "Thời điểm tạo bài", example = "2026-10-14T10:00:00Z")
        Instant createdAt,

        @Schema(description = "Thời điểm cập nhật gần nhất", example = "2026-10-14T10:05:00Z")
        Instant updatedAt) {}
