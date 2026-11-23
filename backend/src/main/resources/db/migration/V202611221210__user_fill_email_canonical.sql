-- Quy tắc: chữ thường, bỏ khoảng trắng. Với gmail.com và googlemail.com: bỏ phần từ dấu + trở đi,
-- bỏ dấu chấm trong phần tên và đổi tên miền về gmail.com. Phải khớp EmailCanonical.of trong Java.
CREATE OR REPLACE FUNCTION f_email_canonical(raw_email TEXT) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
  cleaned    TEXT := lower(regexp_replace(raw_email, '\s', '', 'g'));
  at_pos     INT  := position('@' IN cleaned);
  local_part TEXT;
  domain     TEXT;
BEGIN
  IF at_pos = 0 THEN
    RETURN cleaned;
  END IF;
  local_part := substr(cleaned, 1, at_pos - 1);
  domain := substr(cleaned, at_pos + 1);
  IF domain IN ('gmail.com', 'googlemail.com') THEN
    local_part := replace(split_part(local_part, '+', 1), '.', '');
    domain := 'gmail.com';
  END IF;
  RETURN local_part || '@' || domain;
END;
$$;

-- Mọi đường ghi users (kể cả script và test) đều có email_canonical đúng, không phụ thuộc code Java.
CREATE OR REPLACE FUNCTION set_users_email_canonical() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.email_canonical := f_email_canonical(NEW.email);
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_email_canonical BEFORE INSERT OR UPDATE OF email ON users
  FOR EACH ROW EXECUTE FUNCTION set_users_email_canonical();

UPDATE users SET email_canonical = f_email_canonical(email);

ALTER TABLE users ALTER COLUMN email_canonical SET NOT NULL;
