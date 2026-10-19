package com.shareloop.reputation.entity;

import com.shareloop.common.entity.SliceAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/**
 * Entity lát cắt của bảng users: chỉ map nhóm cột uy tín của module reputation (Lộ trình 4.7).
 * Không tạo bản ghi ở đây (user do module user tạo), chỉ đọc và sửa điểm, sao, hạng.
 */
@Entity
@Table(name = "users")
@SQLRestriction("is_deleted = false")
public class UserReputation extends SliceAudit {

    @Id
    private Long id;

    @Column(name = "trust_stars", nullable = false)
    private short trustStars;

    @Column(name = "total_points", nullable = false)
    private int totalPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "rank_level", nullable = false)
    private Rank rankLevel;

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
}
