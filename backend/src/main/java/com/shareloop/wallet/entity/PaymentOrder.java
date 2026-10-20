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
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "callback_data", columnDefinition = "jsonb")
    private Map<String, String> callbackData;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void submit(Instant now) {
        if (submittedAt == null) submittedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void review(long adminId, Instant now, String note) {
        reviewedBy = adminId;
        reviewedAt = now.truncatedTo(ChronoUnit.MICROS);
        reviewNote = note;
    }

    public void markExpired() {
        if (status == PaymentOrderStatus.PENDING) status = PaymentOrderStatus.EXPIRED;
    }

    protected PaymentOrder() {}

    public void recordGatewayResult(String providerTxnId, boolean signatureVerified, Map<String, String> callbackData) {
        this.providerTxnId = providerTxnId;
        this.signatureVerified = signatureVerified;
        this.callbackData = Map.copyOf(callbackData);
    }

    public void markFailed() {
        if (status == PaymentOrderStatus.PENDING) {
            status = PaymentOrderStatus.FAILED;
        }
    }

    public Map<String, String> getCallbackData() {
        return callbackData;
    }

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
     * Chốt đơn đã thanh toán sau khi cổng xác nhận hoặc admin duyệt. MANUAL không có chữ ký cổng thanh toán;
     * giữ nguyên hành vi xác nhận chữ ký của MOCK/VNPAY.
     */
    public void markSuccess(Instant paidAt) {
        this.status = PaymentOrderStatus.SUCCESS;
        this.signatureVerified = provider != PaymentProvider.MANUAL;
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
