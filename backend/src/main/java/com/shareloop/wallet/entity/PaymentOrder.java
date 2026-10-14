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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Một đơn nạp Credit (bảng payment_orders, ERD mục 7.6). Bảng này không có is_active, is_deleted, created_by,
 * updated_by nên entity không kế thừa BaseEntity (giống {@link CreditLedgerEntry}).
 *
 * <p>Cột updated_at do trigger trg_payment_orders_updated_at của DB tự cập nhật nên không map ở đây.
 * Chỉ {@code CreditService.topUp} được chuyển đơn sang SUCCESS, sau khi đã khoá dòng users rồi tới dòng đơn.
 */
@Entity
@Table(name = "payment_orders")
// AuditingEntityListener: Spring Data tự điền @CreatedDate khi insert.
@EntityListeners(AuditingEntityListener.class)
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "order_code", nullable = false, updatable = false, length = 40)
    private String orderCode;

    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    // Cột DB là INT nên khai int, giống credit_ledger.amount.
    @Column(name = "credits", nullable = false, updatable = false)
    private int credits;

    // Lưu tỉ lệ tại thời điểm nạp để đối soát đúng dù Admin đổi cấu hình sau này (SRS 6.3).
    @Column(name = "vnd_per_credit", nullable = false, updatable = false)
    private int vndPerCredit;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false, length = 20)
    private PaymentProvider provider;

    @Column(name = "provider_txn_id", length = 60)
    private String providerTxnId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private PaymentOrderStatus status;

    @Column(name = "signature_verified", nullable = false)
    private boolean signatureVerified;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected PaymentOrder() {}

    /** Đơn mới luôn ở trạng thái PENDING, chưa kiểm chữ ký. */
    public PaymentOrder(
            Long userId, String orderCode, long amountVnd, int credits, int vndPerCredit, PaymentProvider provider) {
        this.userId = userId;
        this.orderCode = orderCode;
        this.amountVnd = amountVnd;
        this.credits = credits;
        this.vndPerCredit = vndPerCredit;
        this.provider = provider;
        this.status = PaymentOrderStatus.PENDING;
        this.signatureVerified = false;
    }

    /**
     * Chốt đơn đã thanh toán. Người gọi chỉ gọi hàm này sau khi cổng đã xác nhận (với VNPay là đã kiểm chữ ký IPN;
     * với cổng giả lập là MockPaymentClient luôn xác nhận) nên đặt signature_verified = true.
     */
    public void markSuccess(Instant paidAt) {
        this.status = PaymentOrderStatus.SUCCESS;
        this.signatureVerified = true;
        this.paidAt = paidAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public long getAmountVnd() {
        return amountVnd;
    }

    public int getCredits() {
        return credits;
    }

    public int getVndPerCredit() {
        return vndPerCredit;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getProviderTxnId() {
        return providerTxnId;
    }

    public PaymentOrderStatus getStatus() {
        return status;
    }

    public boolean isSignatureVerified() {
        return signatureVerified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
