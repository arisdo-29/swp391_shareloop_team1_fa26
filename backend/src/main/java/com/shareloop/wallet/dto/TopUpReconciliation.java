package com.shareloop.wallet.dto;

/**
 * Dòng đối chiếu tiền mặt với Credit TopUp (FR-110, BR-P09).
 *
 * @param cashCredits tổng Credit của các đơn nạp SUCCESS (tiền mặt thực thu quy ra Credit)
 * @param topUpCredits tổng Credit đã cộng vào sổ cái với type TOP_UP
 * @param difference cashCredits - topUpCredits; dương là có tiền thu mà chưa cộng Credit, âm là ngược lại
 * @param matched true khi difference bằng 0
 */
public record TopUpReconciliation(long cashCredits, long topUpCredits, long difference, boolean matched) {}
