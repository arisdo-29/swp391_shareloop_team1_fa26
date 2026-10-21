package com.shareloop.admin.dto;

import java.time.Instant;
import java.util.List;

/**
 * Danh sách cảnh báo bất thường cho Admin (FR-113, UC-52), tính trực tiếp lúc gọi.
 *
 * @param generatedAt thời điểm tính
 * @param pairWindowDays số ngày của cửa sổ tính cặp giao dịch
 * @param pairThreshold cặp có hơn số giao dịch này trong cửa sổ thì bị cảnh báo
 * @param repeatedPairs các cặp giao dịch lặp, nhiều giao dịch nhất lên trước; rỗng khi không có cảnh báo nào
 */
public record AnomalyReportResponse(
        Instant generatedAt, int pairWindowDays, int pairThreshold, List<RepeatedPairAnomaly> repeatedPairs) {}
