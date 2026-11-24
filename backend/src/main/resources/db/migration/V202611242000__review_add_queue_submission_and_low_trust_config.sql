ALTER TABLE items ADD COLUMN review_submitted_at timestamptz;

INSERT INTO website_attributes (attr_group, attr_key, attr_value, sort_order)
VALUES ('CONFIG', 'review_low_trust_max_stars', '2', 0);
