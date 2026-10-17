-- Keep an administrator's custom timeout while changing the seeded default to 15 seconds.
UPDATE website_attributes
SET attr_value = '15'
WHERE attr_group = 'CONFIG'
  AND attr_key = 'item_ai_timeout_seconds'
  AND attr_value = '20'
  AND is_deleted = FALSE;
