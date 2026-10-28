-- Cập nhật thời hạn khiếu nại sang 24 giờ (1 ngày) và thời hạn giải trình sang 48 giờ (2 ngày) (U08)
UPDATE website_attributes
SET attr_value = '1'
WHERE attr_group = 'CONFIG' AND attr_key = 'complaint_window_days';

UPDATE website_attributes
SET attr_value = '2'
WHERE attr_group = 'CONFIG' AND attr_key = 'response_window_days';
