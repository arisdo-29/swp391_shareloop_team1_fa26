package com.shareloop.review.repository;

import com.shareloop.review.entity.AuthenticityStatus;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthenticityRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthenticityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuthenticityStatus findStatusForUpdate(long itemId) {
        List<AuthenticityStatus> statuses = jdbcTemplate.query(
                "SELECT auth_status FROM items WHERE id = ? AND is_deleted = FALSE FOR UPDATE",
                (resultSet, rowNum) -> AuthenticityStatus.valueOf(resultSet.getString("auth_status")),
                itemId);
        return statuses.stream().findFirst().orElse(null);
    }

    public int countActiveProofs(long itemId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM item_media_mappings mapping
                JOIN media_files media ON media.id = mapping.media_id
                WHERE mapping.item_id = ?
                  AND mapping.role = 'AUTH_PROOF'
                  AND media.status = 'ATTACHED'
                  AND media.visibility = 'PRIVATE'
                  AND media.is_active = TRUE
                  AND media.is_deleted = FALSE
                """, Integer.class, itemId);
    }

    public void updateStatus(long itemId, AuthenticityStatus status) {
        jdbcTemplate.update("UPDATE items SET auth_status = ? WHERE id = ?", status.name(), itemId);
    }
}
