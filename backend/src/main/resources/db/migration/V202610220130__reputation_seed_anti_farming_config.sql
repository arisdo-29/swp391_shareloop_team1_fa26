-- Seed reputation anti-farming and star recovery configuration from SRS Final Report 09/11 (UT-05, UT-08).
-- Restore 1 star after 10 consecutive clean transactions; no points from the 3rd completed transaction between
-- the same pair within 90 days; more than 5 transactions between a pair raises an Admin alert (AD-04, issue #148).
INSERT INTO website_attributes (attr_group, attr_key, attr_value)
VALUES
  ('CONFIG', 'reputation_clean_streak_for_star', '10'),
  ('CONFIG', 'reputation_pair_no_points_from', '3'),
  ('CONFIG', 'reputation_pair_window_days', '90'),
  ('CONFIG', 'reputation_pair_alert_threshold', '5')
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;
