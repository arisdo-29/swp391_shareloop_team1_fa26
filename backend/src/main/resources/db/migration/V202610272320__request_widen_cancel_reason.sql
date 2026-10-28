-- Lý do huỷ giao dịch do người dùng nhập tối đa 500 ký tự, cộng tiền tố người huỷ ("[Chủ bài] "...) nên nới lên 600.
ALTER TABLE requests ALTER COLUMN cancel_reason TYPE VARCHAR(600);
