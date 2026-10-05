-- Dữ liệu mẫu CHỈ cho profile dev (thư mục db/dev-data không nằm trong locations của prod/test).
-- Mật khẩu gốc của cả 3 tài khoản: Dev@12345 (lưu dạng BCrypt, không phải mật khẩu thật).
INSERT INTO users (email, password_hash, full_name, phone, is_admin, status, credit_balance)
VALUES
  ('admin@shareloop.local', '$2a$10$4Hvp0UWQn/YzJwPc.No2aeXjgNxex0B8YyZgg51Ypgpaf50e29ioS',
   'Quản trị viên', '0901000001', TRUE, 'ACTIVE', 0),
  ('an@shareloop.local', '$2a$10$4Hvp0UWQn/YzJwPc.No2aeXjgNxex0B8YyZgg51Ypgpaf50e29ioS',
   'Nguyễn Văn An', '0901000002', FALSE, 'ACTIVE', 100),
  ('binh@shareloop.local', '$2a$10$4Hvp0UWQn/YzJwPc.No2aeXjgNxex0B8YyZgg51Ypgpaf50e29ioS',
   'Trần Thị Bình', '0901000003', FALSE, 'ACTIVE', 100);

-- Mỗi lần số dư đổi phải có dòng sổ cái; seed 100 Credit thì ghi đúng 1 dòng khớp credit_balance.
-- user_id lấy theo email vì id là IDENTITY, không biết trước giá trị.
INSERT INTO credit_ledger (user_id, amount, type, balance_after, note)
SELECT id, 100, 'ADMIN_ADJUST', 100, 'Seed dev'
FROM users
WHERE email IN ('an@shareloop.local', 'binh@shareloop.local');
