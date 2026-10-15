INSERT INTO item_attributes (category_id, name, data_type, options, is_required, sort_order)
SELECT c.id, 'Trạng thái hoạt động', 'SELECT', '["NORMAL", "PARTIAL", "NOT_WORKING"]'::jsonb, true, 1
FROM item_categories c
WHERE c.name = 'Điện tử và phụ kiện'
  AND NOT EXISTS (
    SELECT 1 FROM item_attributes a
    WHERE a.category_id = c.id AND a.name = 'Trạng thái hoạt động' AND NOT a.is_deleted
  );
