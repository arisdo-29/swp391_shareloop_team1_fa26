-- Có trùng email chuẩn hoá trong dữ liệu cũ thì chỉ tạo index thường, không tự gộp hay xoá tài khoản nào.
-- Không trùng thì tạo unique index (cùng điều kiện với ux_users_email).
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM users WHERE NOT is_deleted GROUP BY email_canonical HAVING count(*) > 1
  ) THEN
    CREATE INDEX ix_users_email_canonical ON users (email_canonical) WHERE NOT is_deleted;
  ELSE
    CREATE UNIQUE INDEX ux_users_email_canonical ON users (email_canonical) WHERE NOT is_deleted;
  END IF;
END;
$$;
