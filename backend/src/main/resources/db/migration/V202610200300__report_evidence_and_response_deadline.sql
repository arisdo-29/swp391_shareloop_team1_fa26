ALTER TABLE reports
    ADD COLUMN evidence TEXT,
    ADD COLUMN response_evidence TEXT,
    ADD COLUMN response_due_at TIMESTAMPTZ;
