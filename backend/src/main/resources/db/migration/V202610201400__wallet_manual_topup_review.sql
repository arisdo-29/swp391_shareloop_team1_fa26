ALTER TABLE payment_orders
    ADD COLUMN submitted_at TIMESTAMPTZ,
    ADD COLUMN reviewed_by BIGINT REFERENCES users(id),
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN review_note VARCHAR(500);

CREATE INDEX idx_payment_orders_manual_review
    ON payment_orders (submitted_at DESC, id DESC)
    WHERE provider = 'MANUAL' AND status = 'PENDING' AND submitted_at IS NOT NULL;
