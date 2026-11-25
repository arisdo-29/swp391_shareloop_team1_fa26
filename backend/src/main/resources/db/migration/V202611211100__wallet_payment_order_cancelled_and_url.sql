-- Đơn nạp: tách "đã hủy" (người dùng tự hủy, hủy tại VNPay, hủy để tạo đơn mới) khỏi "thất bại" (cổng báo lỗi), và lưu
-- URL thanh toán để "Tiếp tục thanh toán" dùng lại đúng URL cũ (cùng vnp_TxnRef, vnp_ExpireDate) thay vì tạo đơn mới.

-- CHECK của cột status do V1 khai báo trong dòng nên tên do PostgreSQL tự đặt; tìm theo nội dung để xoá cho chắc.
DO $$
DECLARE
  constraint_name text;
BEGIN
  FOR constraint_name IN
    SELECT con.conname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'payment_orders'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) LIKE '%status%'
      AND pg_get_constraintdef(con.oid) LIKE '%PENDING%'
  LOOP
    EXECUTE format('ALTER TABLE payment_orders DROP CONSTRAINT %I', constraint_name);
  END LOOP;
END $$;

ALTER TABLE payment_orders ADD CONSTRAINT payment_orders_status_check
  CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED', 'CANCELLED'));

ALTER TABLE payment_orders ADD COLUMN payment_url TEXT;

-- Đơn FAILED trước đây thực chất là hủy: người dùng tự hủy trên trang Ví hoặc khách hủy tại VNPay (mã 24).
UPDATE payment_orders SET status = 'CANCELLED'
WHERE status = 'FAILED'
  AND (review_note = 'Bạn đã hủy đơn nạp.' OR callback_data ->> 'vnp_ResponseCode' = '24');

-- Danh sách đơn của một người, mới nhất trước (GET /api/v1/wallet/topup-orders).
CREATE INDEX ix_payment_orders_user_created ON payment_orders (user_id, created_at DESC, id DESC);
