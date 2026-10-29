INSERT INTO website_attributes (attr_group, attr_key, attr_value)
VALUES ('CONFIG', 'post_fee_over_quota', '10')
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;

-- Bản cũ đánh dấu cả bài Cho tặng 0 Credit là đã trả phí đăng.
UPDATE items i
SET post_fee_paid = false
WHERE i.post_fee_paid = true
  AND NOT EXISTS (
    SELECT 1 FROM credit_ledger l
    WHERE l.item_id = i.id AND l.type = 'POST_FEE' AND l.amount < 0
  );
