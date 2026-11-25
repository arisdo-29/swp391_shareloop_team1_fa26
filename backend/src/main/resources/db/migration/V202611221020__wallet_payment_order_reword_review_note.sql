-- Sửa câu ghi chú cũ của đơn nạp đã thu tiền sau khi đóng cho đúng chữ hiển thị (không dùng dấu chấm phẩy).
UPDATE payment_orders
SET review_note = replace(review_note, '; đơn chờ đối soát để hoàn tiền', '. Đơn chờ Admin đối soát')
WHERE review_note LIKE 'VNPay báo đã thu tiền sau khi đơn đã đóng;%';
