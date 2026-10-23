package com.shareloop.reputation.entity;

import com.shareloop.common.entity.SliceAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.SQLRestriction;

/**
 * Entity lát cắt của bảng users: chỉ map nhóm cột uy tín của module reputation (Lộ trình 4.7).
 * Không tạo bản ghi ở đây (user do module user tạo), chỉ đọc và sửa điểm, sao, hạng.
 */
@Entity
@Table(name = "users")
@SQLRestriction("is_deleted = false")
public class UserReputation extends SliceAudit {

    private static final int MAX_TRUST_STARS = 5;

    @Id
    private Long id;

    @Column(name = "trust_stars", nullable = false)
    private short trustStars;

    @Column(name = "total_points", nullable = false)
    private int totalPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "rank_level", nullable = false)
    private Rank rankLevel;

    @Column(name = "points_today", nullable = false)
    private int pointsToday;

    @Column(name = "points_date")
    private LocalDate pointsDate;

    @Column(name = "clean_streak", nullable = false)
    private int cleanStreak;

    @Column(name = "grace_star_used", nullable = false)
    private boolean graceStarUsed;

    protected UserReputation() {}

    public UserReputation(Long id, short trustStars, int totalPoints, Rank rankLevel) {
        this.id = id;
        this.trustStars = trustStars;
        this.totalPoints = totalPoints;
        this.rankLevel = rankLevel;
    }

    public Long getId() {
        return id;
    }

    public int getTrustStars() {
        return trustStars;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public Rank getRankLevel() {
        return rankLevel;
    }

    public int getPointsToday() {
        return pointsToday;
    }

    public LocalDate getPointsDate() {
        return pointsDate;
    }

    public int getCleanStreak() {
        return cleanStreak;
    }

    public boolean isGraceStarUsed() {
        return graceStarUsed;
    }

    public void addPoints(int points, Rank newRank) {
        this.totalPoints += points;
        this.rankLevel = newRank;
    }

    public int deductStars(int amount) {
        int before = this.trustStars;
        this.trustStars = (short) Math.max(0, this.trustStars - amount);
        return before;
    }

    public void setTrustStars(short trustStars) {
        this.trustStars = (short) Math.max(0, Math.min(5, (int) trustStars));
    }

    public void setTotalPoints(int totalPoints) {
        this.totalPoints = Math.max(0, totalPoints);
    }

    public void setRankLevel(Rank rankLevel) {
        this.rankLevel = rankLevel;
    }

    public void setCleanStreak(int cleanStreak) {
        this.cleanStreak = Math.max(0, cleanStreak);
    }

    /**
     * Lấy phần điểm còn được cộng trong ngày (BR-U11). Sang ngày mới thì points_today đếm lại từ 0.
     *
     * @param points số điểm muốn cộng
     * @param dailyCap trần điểm mỗi ngày
     * @param today ngày hiện tại theo giờ Việt Nam
     * @return số điểm thực được cộng, đã cộng vào points_today
     */
    public int claimDailyPoints(int points, int dailyCap, LocalDate today) {
        if (!today.equals(pointsDate)) {
            pointsToday = 0;
            pointsDate = today;
        }
        int granted = Math.max(0, Math.min(points, dailyCap - pointsToday));
        pointsToday += granted;
        return granted;
    }

    /**
     * Ghi thêm một giao dịch sạch (BR-U09). Chuỗi đủ ngưỡng mà chưa đủ 5 sao thì hồi 1 sao và đếm lại từ 0.
     *
     * @param streakForStar số giao dịch sạch liên tiếp để hồi 1 sao
     * @return true nếu vừa hồi sao
     */
    public boolean recordCleanTransaction(int streakForStar) {
        cleanStreak++;
        if (cleanStreak >= streakForStar && trustStars < MAX_TRUST_STARS) {
            trustStars++;
            cleanStreak = 0;
            return true;
        }
        return false;
    }

    /** Vi phạm làm mất chuỗi giao dịch sạch (BR-U09). */
    public void resetCleanStreak() {
        this.cleanStreak = 0;
    }

    /** Cấp 1 sao ân hạn (FR-95); grace_star_used chỉ đổi false → true. */
    public void grantGraceStar() {
        this.trustStars = 1;
        this.graceStarUsed = true;
    }
}
