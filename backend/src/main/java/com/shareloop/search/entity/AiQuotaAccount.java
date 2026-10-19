package com.shareloop.search.entity;

import com.shareloop.common.entity.SliceAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.SQLRestriction;

/** Search-owned columns of users. The wallet owns the top-up flag and Credit balance. */
@Entity
@Table(name = "users")
@SQLRestriction("is_deleted = false")
public class AiQuotaAccount extends SliceAudit {
    @Id
    private Long id;

    @Column(name = "free_ai_used", nullable = false)
    private short freeAiUsed;

    @Column(name = "free_ai_date")
    private LocalDate freeAiDate;

    protected AiQuotaAccount() {}

    public int getFreeAiUsed() {
        return freeAiUsed;
    }

    public LocalDate getFreeAiDate() {
        return freeAiDate;
    }

    public void use(LocalDate date, boolean toppedUp) {
        if (toppedUp && !date.equals(freeAiDate)) {
            freeAiUsed = 0;
            freeAiDate = date;
        }
        freeAiUsed++;
    }

    public void refund(LocalDate reservedDate, boolean toppedUp) {
        if (((!toppedUp && freeAiDate == null) || (toppedUp && reservedDate.equals(freeAiDate))) && freeAiUsed > 0) {
            freeAiUsed--;
        }
    }
}
