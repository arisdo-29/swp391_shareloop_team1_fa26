ALTER TABLE reports
    DROP CONSTRAINT reports_target_type_check,
    ADD CONSTRAINT reports_target_type_check CHECK (target_type IN ('ITEM', 'USER', 'REQUEST', 'SYSTEM')),
    ADD COLUMN system_title VARCHAR(150),
    ADD CONSTRAINT reports_system_fields_check CHECK (
        target_type <> 'SYSTEM' OR (
            system_title IS NOT NULL AND item_id IS NULL AND target_user_id IS NULL AND request_id IS NULL
            AND reason = 'OTHER' AND status IN ('OPEN', 'RESOLVED') AND stars_deducted = 0
        )
    );

CREATE INDEX idx_reports_system_created ON reports (created_at DESC, id DESC)
    WHERE target_type = 'SYSTEM' AND NOT is_deleted;

CREATE INDEX idx_reports_system_reporter_created ON reports (reporter_id, created_at DESC, id DESC)
    WHERE target_type = 'SYSTEM' AND NOT is_deleted;
