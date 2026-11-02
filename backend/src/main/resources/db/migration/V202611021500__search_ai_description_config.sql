INSERT INTO website_attributes (attr_group, attr_key, attr_value, is_active, is_deleted)
VALUES
  ('CONFIG', 'ai_describe_per_day', '5', TRUE, FALSE),
  ('CONFIG', 'ai_describe_timeout_seconds', '20', TRUE, FALSE)
ON CONFLICT DO NOTHING;
