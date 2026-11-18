package com.shareloop.wallet.dto;

import java.util.List;

/**
 * Dòng đối chiếu tiền mặt với Credit TopUp (VI-09).
 *
 * @param cashCredits tổng Credit của các đơn nạp SUCCESS (tiền mặt thực thu quy ra Credit)
 * @param topUpCredits tổng Credit TOP_UP gắn với đơn SUCCESS theo ngày thanh toán của đơn
 * @param difference cashCredits - topUpCredits; dương là có tiền thu mà chưa cộng Credit, âm là ngược lại
 * @param matched true khi difference bằng 0 và không có dòng thiếu đơn
 * @param unlinkedEntryCount số dòng TOP_UP thiếu đơn trong kỳ ghi sổ
 * @param unlinkedCredits tổng Credit của các dòng thiếu đơn
 * @param unlinkedEntries tối đa năm dòng thiếu đơn gần nhất để tra cứu
 */
public record TopUpReconciliation(
        long cashCredits,
        long topUpCredits,
        long difference,
        boolean matched,
        long unlinkedEntryCount,
        long unlinkedCredits,
        List<UnlinkedTopUp> unlinkedEntries) {

    public TopUpReconciliation(long cashCredits, long topUpCredits, long difference, boolean matched) {
        this(cashCredits, topUpCredits, difference, matched, 0, 0, List.of());
    }
}
