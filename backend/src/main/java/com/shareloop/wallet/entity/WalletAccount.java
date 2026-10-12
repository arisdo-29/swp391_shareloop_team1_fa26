package com.shareloop.wallet.entity;

import com.shareloop.common.entity.SliceAudit;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.wallet.WalletErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/**
 * Entity "lát cắt" của bảng users: chỉ map nhóm cột tiền của module wallet (Lộ trình 4.8). Không tạo bản ghi ở đây
 * (user do module user tạo), chỉ đọc và sửa số dư.
 *
 * <p>Các hàm đổi số dư ({@code hold}, {@code release}, {@code chargeHeld}, {@code spend}) chỉ do
 * {@code CreditService} gọi, sau khi đã khoá dòng bằng {@code WalletAccountRepository.lockById}. Chúng để public vì
 * entity và service nằm ở hai package khác nhau; module khác không có repository nên không với tới entity này.
 */
@Entity
@Table(name = "users")
@SQLRestriction("is_deleted = false") // tài khoản xoá mềm coi như không có ví
public class WalletAccount extends SliceAudit {

    // Không @GeneratedValue: lát cắt không bao giờ insert, id luôn lấy từ bản ghi do module user tạo.
    @Id
    private Long id;

    @Column(name = "credit_balance", nullable = false)
    private long creditBalance;

    @Column(name = "held_credit", nullable = false)
    private long heldCredit;

    @Column(name = "has_topped_up", nullable = false)
    private boolean hasToppedUp;

    protected WalletAccount() {}

    public Long getId() {
        return id;
    }

    public long getCreditBalance() {
        return creditBalance;
    }

    public long getHeldCredit() {
        return heldCredit;
    }

    public boolean hasToppedUp() {
        return hasToppedUp;
    }

    /** Số Credit dùng được = tổng có trừ phần đang giữ. */
    public long available() {
        return creditBalance - heldCredit;
    }

    /** Giữ chỗ: chỉ tăng held_credit, không đổi credit_balance. */
    public void hold(long amount) {
        if (available() < amount) {
            throw new BusinessException(WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
        }
        heldCredit += amount;
    }

    /** Nhả phần đang giữ: chỉ giảm held_credit, không đổi credit_balance. */
    public void release(long amount) {
        if (heldCredit < amount) {
            throw new BusinessException(WalletErrorCode.WALLET_HELD_INSUFFICIENT);
        }
        heldCredit -= amount;
    }

    /** Trừ khoản đang giữ: giảm cả held_credit và credit_balance cùng lúc. */
    public void chargeHeld(long amount) {
        if (heldCredit < amount) {
            throw new BusinessException(WalletErrorCode.WALLET_HELD_INSUFFICIENT);
        }
        heldCredit -= amount;
        creditBalance -= amount;
    }

    /** Trừ ngay, chỉ dùng phần khả dụng; Credit đang giữ cho bài khác không bị đụng tới. */
    public void spend(long amount) {
        if (available() < amount) {
            throw new BusinessException(WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
        }
        creditBalance -= amount;
    }
}
