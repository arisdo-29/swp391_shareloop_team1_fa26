package com.shareloop.wallet.dto;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tổng hợp số liệu nạp tiền theo từng thành viên phục vụ xuất báo cáo tài chính (L02).
 *
 * @param userId mã người dùng
 * @param fullName họ và tên
 * @param email địa chỉ email
 * @param totalAmountVnd tổng số tiền đã nạp (VND)
 * @param totalCredits tổng số Credit nhận được
 * @param orderCount tổng số đơn nạp thành công
 */
public record MemberTopUpSummary(
        Long userId, String fullName, String email, long totalAmountVnd, long totalCredits, long orderCount) {

    /**
     * Gom nhóm và tổng hợp từ danh sách chi tiết đơn nạp, sắp xếp giảm dần theo tổng tiền nạp.
     */
    public static List<MemberTopUpSummary> fromDetails(List<TopUpReportItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<Long, List<TopUpReportItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(TopUpReportItem::userId, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<TopUpReportItem> userOrders = entry.getValue();
                    TopUpReportItem first = userOrders.get(0);
                    long totalAmount = userOrders.stream()
                            .mapToLong(TopUpReportItem::amountVnd)
                            .sum();
                    long totalCredits = userOrders.stream()
                            .mapToLong(TopUpReportItem::credits)
                            .sum();
                    return new MemberTopUpSummary(
                            entry.getKey(),
                            first.fullName(),
                            first.email(),
                            totalAmount,
                            totalCredits,
                            userOrders.size());
                })
                .sorted(Comparator.comparingLong(MemberTopUpSummary::totalAmountVnd)
                        .reversed()
                        .thenComparing(MemberTopUpSummary::fullName))
                .toList();
    }
}
