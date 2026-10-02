INSERT INTO website_attributes (attr_group, attr_key, attr_value, sort_order)
VALUES
  ('CONFIG', 'otp_ttl_minutes', '5', 0),
  ('CONFIG', 'otp_max_failed', '5', 0),
  ('CONFIG', 'otp_resend_cooldown_seconds', '60', 0),
  ('CONFIG', 'pending_account_ttl_hours', '24', 0);
