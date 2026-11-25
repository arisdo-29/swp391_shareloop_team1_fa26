-- Đơn VNPAY đã đóng (FAILED, CANCELLED, EXPIRED) nhưng đã lưu callback thành công đúng chữ ký thì VNPay đã thu tiền
-- mà Credit chưa cộng. Từ nay hai nhánh xử lý đó ghi paid_at lúc callback tới, và trang Giao dịch nạp tiền của Admin
-- dựa vào paid_at để gắn nhãn "Cần đối soát". Migration này chỉ gán paid_at cho các đơn cũ có bằng chứng chắc chắn:
-- callback_data đã lưu có vnp_ResponseCode và vnp_TransactionStatus cùng bằng 00 và signature_verified = true.
-- Không suy từ chuỗi trong review_note. Đơn chỉ có ghi chú mà không có callback thì không đụng tới.
-- updated_at là lúc đơn bị đổi trạng thái khi callback tới nên dùng làm thời điểm tiền về gần đúng.
UPDATE payment_orders
SET paid_at = updated_at
WHERE provider = 'VNPAY'
  AND status IN ('FAILED', 'CANCELLED', 'EXPIRED')
  AND paid_at IS NULL
  AND signature_verified = TRUE
  AND callback_data ->> 'vnp_ResponseCode' = '00'
  AND callback_data ->> 'vnp_TransactionStatus' = '00';
