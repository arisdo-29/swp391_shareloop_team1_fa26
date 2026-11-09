-- Seed reputation rank thresholds from SRS Final Report 09/11, section 5.2 (reputation_silver_min, reputation_gold_min, reputation_diamond_min).
-- Bronze: 0-49, Silver: 50-149, Gold: 150-399, Diamond: >= 400.
INSERT INTO website_attributes (attr_group, attr_key, attr_value)
VALUES
  ('CONFIG', 'reputation_silver_min', '50'),
  ('CONFIG', 'reputation_gold_min', '150'),
  ('CONFIG', 'reputation_diamond_min', '400'),
  ('CONFIG', 'REPUTATION_SILVER_MIN', '50'),
  ('CONFIG', 'REPUTATION_GOLD_MIN', '150'),
  ('CONFIG', 'REPUTATION_DIAMOND_MIN', '400')
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;

