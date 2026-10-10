package com.shareloop.listingfee.entity;

import com.shareloop.common.entity.SliceAudit;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.listingfee.ListingFeeErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.SQLRestriction;

/**
 * Entity "lát cắt" của bảng items: chỉ map nhóm cột phí của module listingfee (Lộ trình 4.8). Không tạo bản ghi ở đây
 * (bài do module item tạo), chỉ đọc và sửa cột phí. donor_id không map ở đây vì Item đã map cột đó.
 *
 * <p>Các hàm đổi trạng thái chỉ do {@code PostFeeService} gọi, sau khi đã khoá dòng bằng
 * {@code ItemFeeRepository.lockById}.
 */
@Entity
@Table(name = "items")
@SQLRestriction("is_deleted = false") // bài đã xoá mềm coi như không có
public class ItemFee extends SliceAudit {

    // Không @GeneratedValue: lát cắt không bao giờ insert, id luôn lấy từ bản ghi do module item tạo.
    @Id
    private Long id;

    @Column(name = "post_fee_paid", nullable = false)
    private boolean postFeePaid;

    // Cột cho phép NULL: bài chưa từng giữ phí thì chưa có loại.
    @Enumerated(EnumType.STRING)
    @Column(name = "pending_fee_type", length = 10)
    private ItemFeeType pendingFeeType;

    // Cột DB là INT nên khai int (ddl-auto: validate kiểm kiểu).
    @Column(name = "pending_fee", nullable = false)
    private int pendingFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_state", nullable = false, length = 10)
    private ItemFeeState feeState;

    @Column(name = "free_edit_used", nullable = false)
    private boolean freeEditUsed;

    // Cột DB là SMALLINT nên khai short.
    @Column(name = "approved_edit_count", nullable = false)
    private short approvedEditCount;

    @Column(name = "expire_at")
    private Instant expireAt;

    @Column(name = "boosted_until")
    private Instant boostedUntil;

    protected ItemFee() {}

    public Long getId() {
        return id;
    }

    public boolean isPostFeePaid() {
        return postFeePaid;
    }

    public ItemFeeType getPendingFeeType() {
        return pendingFeeType;
    }

    public int getPendingFee() {
        return pendingFee;
    }

    public ItemFeeState getFeeState() {
        return feeState;
    }

    public boolean isFreeEditUsed() {
        return freeEditUsed;
    }

    public short getApprovedEditCount() {
        return approvedEditCount;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public Instant getBoostedUntil() {
        return boostedUntil;
    }

    /** Ghi nhận đang giữ phí; từ CHARGED hoặc RELEASED vẫn giữ lại được (lượt sửa sau). */
    public void markHeld(int fee, ItemFeeType type) {
        if (feeState == ItemFeeState.HELD) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_ALREADY_HELD);
        }
        this.pendingFee = fee;
        this.pendingFeeType = type;
        this.feeState = ItemFeeState.HELD;
    }

    /** Khoản giữ đã bị trừ; phí đăng (POST) thì đánh dấu bài đã trả phí đăng. pending_fee giữ nguyên để truy vết. */
    public void markCharged() {
        requireHeld();
        this.feeState = ItemFeeState.CHARGED;
        if (pendingFeeType == ItemFeeType.POST) {
            this.postFeePaid = true;
        }
    }

    /** Khoản giữ đã được nhả; pending_fee giữ nguyên để truy vết. */
    public void markReleased() {
        requireHeld();
        this.feeState = ItemFeeState.RELEASED;
    }

    private void requireHeld() {
        if (feeState != ItemFeeState.HELD) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_NOTHING_HELD);
        }
    }
}
