-- Khoá đăng nhập tạm sau nhiều lần sai mật khẩu (A2, rà soát nghiệp vụ bản 2).
ALTER TABLE users
  ADD COLUMN login_failed_count SMALLINT NOT NULL DEFAULT 0,
  ADD COLUMN login_locked_until TIMESTAMPTZ;
