package com.shareloop.admin.dto;

import com.shareloop.user.dto.SharedIpGroup;
import java.time.Instant;
import java.util.List;

/**
 * Danh sách cảnh báo bất thường cho Admin (FR-113, UC-52), tính trực tiếp lúc gọi.
 *
 * @param generatedAt thời điểm tính
 * @param pairWindowDays số ngày của cửa sổ tính cặp giao dịch
 * @param pairThreshold cặp có hơn số giao dịch này trong cửa sổ thì bị cảnh báo
 * @param repeatedPairs các cặp giao dịch lặp, nhiều giao dịch nhất lên trước; rỗng khi không có cảnh báo nào
 * @param ipWindowDays số ngày của cửa sổ tính nhiều tài khoản cùng IP
 * @param ipMinAccounts số tài khoản tối thiểu cùng IP trong cửa sổ để cảnh báo
 * @param sharedIpGroups các nhóm IP có từ ipMinAccounts tài khoản đăng ký trong ipWindowDays
 */
public record AnomalyReportResponse(
        Instant generatedAt,
        int pairWindowDays,
        int pairThreshold,
        List<RepeatedPairAnomaly> repeatedPairs,
        int ipWindowDays,
        int ipMinAccounts,
        List<SharedIpGroup> sharedIpGroups) {

    public AnomalyReportResponse(
            Instant generatedAt, int pairWindowDays, int pairThreshold, List<RepeatedPairAnomaly> repeatedPairs) {
        this(generatedAt, pairWindowDays, pairThreshold, repeatedPairs, 30, 3, List.of());
    }
}
