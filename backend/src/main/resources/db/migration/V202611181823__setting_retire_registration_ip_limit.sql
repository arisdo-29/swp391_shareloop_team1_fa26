-- TK-05: bỏ khoá chặn đăng ký và nâng ngưỡng cảnh báo mặc định từ 3 lên 5.
UPDATE website_attributes
SET is_deleted = TRUE
WHERE attr_group = 'CONFIG' AND attr_key = 'accounts_per_ip_24h' AND NOT is_deleted;

-- Giữ các giá trị đã chỉnh khác 3 nếu còn hợp lệ.
UPDATE website_attributes
SET attr_value = '5'
WHERE attr_group = 'CONFIG' AND attr_key = 'ip_alert_min_accounts' AND NOT is_deleted
  AND trim(attr_value) = '3';

UPDATE website_attributes
SET attr_value = '2'
WHERE attr_group = 'CONFIG' AND attr_key = 'ip_alert_min_accounts' AND NOT is_deleted
  AND trim(attr_value) ~ '^(-[0-9]+|[+]?0*[01])$';
