INSERT INTO website_attributes (attr_group, attr_key, attr_value, is_active, is_deleted)
VALUES
  ('CONFIG', 'review_newcomer_max_approved', '3', TRUE, FALSE)
ON CONFLICT DO NOTHING;
