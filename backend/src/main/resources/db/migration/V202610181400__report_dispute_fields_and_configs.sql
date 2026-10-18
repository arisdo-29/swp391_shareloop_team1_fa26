-- Migration for Issue #112: Transaction dispute fields and configs (FR-89, FR-90, BR-T08).

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS response_due_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS evidence TEXT,
    ADD COLUMN IF NOT EXISTS response_evidence TEXT;

INSERT INTO website_attributes (attr_group, attr_key, attr_value)
VALUES
    ('CONFIG', 'dispute_window_days', '7'),
    ('CONFIG', 'DISPUTE_WINDOW_DAYS', '7'),
    ('CONFIG', 'dispute_response_days', '3'),
    ('CONFIG', 'DISPUTE_RESPONSE_DAYS', '3')
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;

