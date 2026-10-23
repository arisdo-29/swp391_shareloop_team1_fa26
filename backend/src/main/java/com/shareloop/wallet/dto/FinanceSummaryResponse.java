package com.shareloop.wallet.dto;

import java.time.Instant;
import java.util.List;

/**
 * Báo cáo ba sổ tài chính trong khoảng [from, to) (FR-109, FR-110, BR-P09; SRS mục 4.7).
 *
 * @param from mốc bắt đầu (gồm) đúng như người gọi truyền, null là không giới hạn
 * @param to mốc kết thúc (không gồm) đúng như người gọi truyền, null là không giới hạn
 * @param cashOrderCount số đơn nạp SUCCESS có paid_at trong khoảng
 * @param cashAmountVnd tổng tiền mặt thực thu (VND) của các đơn đó
 * @param creditInTotal tổng Credit cộng vào (các dòng credit_ledger amount dương)
 * @param creditInByType Credit cộng vào tách theo type, chỉ gồm type có dữ liệu, sắp theo tên type
 * @param creditOutTotal tổng Credit trừ ra (các dòng amount âm), trả số dương
 * @param creditOutByType Credit trừ ra tách theo type, số dương, chỉ gồm type có dữ liệu, sắp theo tên type
 * @param reconciliation đối chiếu tiền mặt quy ra Credit với tổng TOP_UP
 * @param cashByDay tiền mặt thực thu theo từng ngày giờ Việt Nam, ngày cũ trước, chỉ gồm ngày có đơn thanh toán
 */
public record FinanceSummaryResponse(
        Instant from,
        Instant to,
        long cashOrderCount,
        long cashAmountVnd,
        long creditInTotal,
        List<CreditTypeTotal> creditInByType,
        long creditOutTotal,
        List<CreditTypeTotal> creditOutByType,
        TopUpReconciliation reconciliation,
        List<DailyCashTotal> cashByDay) {}
