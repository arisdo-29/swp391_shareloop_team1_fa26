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
 * Một đơn nạp Credit (bảng payment_orders). Bảng này không có is_active, is_deleted, created_by,
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

    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl;

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

    /** Khách hủy tại cổng thanh toán (VNPay mã 24): PENDING → CANCELLED. Trạng thái khác giữ nguyên. */
    public void markCancelled() {
        if (status == PaymentOrderStatus.PENDING) {
            status = PaymentOrderStatus.CANCELLED;
        }
    }

    /** Lưu URL thanh toán vừa dựng để "Tiếp tục thanh toán" mở lại đúng URL đó (cùng vnp_TxnRef, vnp_ExpireDate). */
    public void attachPaymentUrl(String url) {
        this.paymentUrl = url;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public void markFailed() {
        if (status == PaymentOrderStatus.PENDING) {
            status = PaymentOrderStatus.FAILED;
        }
    }

    /**
     * Cổng báo đã thu tiền cho đơn đã kết thúc mà không cộng Credit (FAILED, EXPIRED): giữ nguyên trạng thái, ghi chú để
     * đối soát và hoàn tiền tay. Không đè callback_data để còn giữ mã lỗi lúc đơn bị hủy.
     */
    /**
     * VNPay báo đã thu tiền nhưng đơn đã đóng (FAILED, CANCELLED, EXPIRED): không cộng Credit, chỉ ghi lại bằng chứng
     * tiền đã về để Admin đối soát. paid_at ở đây là lúc VNPay báo đã thu, không có nghĩa đơn đã nạp thành công; mọi
     * chỗ tính tiền thật đều lọc thêm status = SUCCESS.
     */
    public void flagPaidAfterClosed(String note, Instant paidAt, String transactionNo) {
        reviewNote = note;
        markPaidEvidence(paidAt, transactionNo);
    }

    /** Đơn PENDING quá hạn nhưng VNPay đã thu tiền: chuyển EXPIRED và ghi bằng chứng tiền đã về để đối soát. */
    public void expireWithPaidEvidence(Instant paidAt, String transactionNo) {
        markExpired();
        markPaidEvidence(paidAt, transactionNo);
    }

    // Không đè paid_at đã có, vì lần báo đầu tiên mới là lúc tiền về.
    private void markPaidEvidence(Instant paidAt, String transactionNo) {
        if (this.paidAt == null) {
            this.paidAt = paidAt.truncatedTo(ChronoUnit.MICROS);
        }
        if (transactionNo != null && !transactionNo.isBlank()) {
            this.providerTxnId = transactionNo;
        }
    }

    /**
     * Đơn VNPAY đã đóng mà VNPay đã thu tiền (có paid_at) và Admin chưa xử lý. Đơn SUCCESS hoặc PENDING không bao giờ
     * cần đối soát.
     */
    public boolean needsReconcile() {
        boolean closedUnpaid = status == PaymentOrderStatus.FAILED
                || status == PaymentOrderStatus.CANCELLED
                || status == PaymentOrderStatus.EXPIRED;
        return provider == PaymentProvider.VNPAY && closedUnpaid && paidAt != null && reviewedAt == null;
    }

    /** Admin xác nhận đã thu tiền và cộng Credit: đơn sang SUCCESS, giữ nguyên paid_at (lúc tiền thật về). */
    public void markReconciledGranted(long adminId, Instant now, String note) {
        this.status = PaymentOrderStatus.SUCCESS;
        this.signatureVerified = true;
        review(adminId, now, note);
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
