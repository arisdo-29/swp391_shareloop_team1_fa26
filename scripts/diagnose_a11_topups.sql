-- Truy vấn chỉ đọc để đối chiếu đơn nạp với sổ cái trước khi sửa dữ liệu staging.
SELECT l.id, l.user_id, l.amount, l.created_at, l.note,
       o.order_code, o.status, o.credits, o.paid_at
FROM credit_ledger l
LEFT JOIN payment_orders o ON o.id = l.payment_order_id
WHERE l.type = 'TOP_UP'
  AND (o.id IS NULL OR o.status <> 'SUCCESS' OR o.credits <> l.amount OR o.user_id <> l.user_id
       OR date(o.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')
          IS DISTINCT FROM date(l.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh'))
ORDER BY l.id;

SELECT o.id, o.order_code, o.credits, o.paid_at
FROM payment_orders o
WHERE o.status = 'SUCCESS'
  AND NOT EXISTS (
      SELECT 1 FROM credit_ledger l
      WHERE l.payment_order_id = o.id AND l.type = 'TOP_UP' AND l.user_id = o.user_id
  )
ORDER BY o.id;
