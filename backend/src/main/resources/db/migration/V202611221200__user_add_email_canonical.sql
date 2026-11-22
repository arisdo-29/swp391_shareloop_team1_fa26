-- Cột email chuẩn hoá để kiểm trùng (A3). Email gốc vẫn dùng để gửi thư và hiển thị.
ALTER TABLE users ADD COLUMN email_canonical VARCHAR(255);
