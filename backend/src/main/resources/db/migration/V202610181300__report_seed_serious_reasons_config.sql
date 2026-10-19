-- Seed serious report reasons configuration (FR-93, BR-A07).
-- When an UPHOLD conclusion is reached for these reasons, other active items of the violator must be hidden for review.
INSERT INTO website_attributes (attr_group, attr_key, attr_value)
VALUES
  ('CONFIG', 'report_serious_reasons', 'PROHIBITED_ITEM,COUNTERFEIT'),
  ('CONFIG', 'REPORT_SERIOUS_REASONS', 'PROHIBITED_ITEM,COUNTERFEIT')
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;

