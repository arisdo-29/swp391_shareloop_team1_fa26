ALTER TABLE requests
    ADD COLUMN schedule_revision BIGINT NOT NULL DEFAULT 0;
