package com.shareloop.admin.dto;

import com.shareloop.audit.dto.ActivityLogDto;
import com.shareloop.item.dto.AdminItemDetail;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import java.util.List;

/**
 * Trang chi tiết bài của Admin: nội dung bài, phí Credit, yêu cầu, báo cáo và lịch sử bài đăng.
 *
 * @param item nội dung bài ở mọi trạng thái (gồm ảnh và thuộc tính)
 * @param summary các thông tin tóm tắt như ở danh sách (chủ bài, trạng thái ẩn, phí đăng, hạn hiển thị)
 * @param feeLines các dòng sổ cái gắn với bài, cũ nhất trước
 * @param hasRunningTransaction true khi có giao dịch đã chọn và đang chạy, nên chưa ẩn bài được
 * @param runningRequestId id giao dịch đang chạy để Admin bấm sang trang Giao dịch, null khi không có
 * @param hiddenReason lý do Admin đã ẩn bài, null khi bài không do Admin ẩn
 * @param visibilityHistory lần Admin ẩn và hiện lại bài, mới nhất trước
 */
public record AdminItemFullDetail(
        AdminItemDetail item,
        AdminItemRow summary,
        List<WalletTransactionResponse> feeLines,
        List<AdminItemRequestRow> requests,
        boolean hasRunningTransaction,
        Long runningRequestId,
        List<AdminItemReportRow> reports,
        String hiddenReason,
        List<ActivityLogDto> visibilityHistory,
        List<AdminItemHistoryEntry> postHistory) {}
