package com.shareloop.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Một dòng sổ cái Credit (bảng credit_ledger, ERD mục 7.6). Sổ cái chỉ thêm dòng mới, không sửa, không xoá nên entity
 * này không kế thừa BaseEntity (không is_active, is_deleted, updated_*) và không có hàm sửa.
 *
 * <p>amount âm là trừ, dương là cộng; balance_after là số dư credit_balance ngay sau giao dịch.
 */
@Entity
@Table(name = "credit_ledger")
// AuditingEntityListener: Spring Data tự điền @CreatedDate, @CreatedBy khi insert.
@EntityListeners(AuditingEntityListener.class)
public class CreditLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // Cột DB là INT nên khai int; kiểu long sẽ làm ddl-auto: validate báo lệch kiểu.
    @Column(name = "amount", nullable = false, updatable = false)
    private int amount;

    // STRING: lưu tên enum, khớp CHECK của cột type.
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 16)
    private CreditTxType type;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private long balanceAfter;

    @Column(name = "item_id", updatable = false)
    private Long itemId;

    @Column(name = "payment_order_id", updatable = false)
    private Long paymentOrderId;

    @Column(name = "note", length = 255, updatable = false)
    private String note;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CreditLedgerEntry() {}

    /** Dòng trừ tiền (phí đăng bài, sửa bài, gia hạn, đẩy bài, AI): amount truyền vào đã là số âm. */
    public CreditLedgerEntry(Long userId, int amount, CreditTxType type, long balanceAfter, Long itemId) {
        this.userId = userId;
        this.amount = amount;
        this.type = type;
        this.balanceAfter = balanceAfter;
        this.itemId = itemId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public int getAmount() {
        return amount;
    }

    public CreditTxType getType() {
        return type;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public Long getItemId() {
        return itemId;
    }

    public Long getPaymentOrderId() {
        return paymentOrderId;
    }

    public String getNote() {
        return note;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
