-- V1__init.sql: 17 bảng theo ERD v10 (chương 7), extension, hàm dùng chung, ràng buộc và index mục 7.7.
-- Quy ước (Lộ trình mục 3.3, ERD mục 7.11): snake_case; id BIGINT identity; enum = VARCHAR + CHECK;
-- created_by / updated_by không đặt khoá ngoại; duy nhất chỉ tính bản ghi chưa xoá mềm.

CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- unaccent() không phải IMMUTABLE nên không dùng trực tiếp trong index; bọc lại:
CREATE OR REPLACE FUNCTION f_unaccent(text) RETURNS text
  LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent', $1) $$;

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger
  LANGUAGE plpgsql AS $$ BEGIN NEW.updated_at := now(); RETURN NEW; END $$;

-- ---------------------------------------------------------------------------
-- Module catalog
CREATE TABLE areas (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  parent_id   BIGINT REFERENCES areas (id),
  level       SMALLINT NOT NULL CHECK (level IN (1, 2)),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  BIGINT,
  updated_by  BIGINT,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE,
  is_deleted  BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TRIGGER trg_areas_updated_at BEFORE UPDATE ON areas
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Module setting
CREATE TABLE website_attributes (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  attr_group  VARCHAR(30) NOT NULL
              CHECK (attr_group IN ('CONFIG', 'BANNED_KEYWORD', 'CHECKLIST', 'REJECT_REASON', 'DISPOSABLE_DOMAIN')),
  attr_key    VARCHAR(100) NOT NULL,
  attr_value  TEXT NOT NULL,
  extra       JSONB,
  sort_order  INT NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  BIGINT,
  updated_by  BIGINT,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE,
  is_deleted  BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TRIGGER trg_website_attributes_updated_at BEFORE UPDATE ON website_attributes
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
-- cấu hình không trùng khoá
CREATE UNIQUE INDEX ux_attr ON website_attributes (attr_group, attr_key) WHERE NOT is_deleted;

-- Module catalog
CREATE TABLE item_categories (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name           VARCHAR(100) NOT NULL,
  parent_id      BIGINT REFERENCES item_categories (id),
  is_restricted  BOOLEAN NOT NULL DEFAULT FALSE,
  is_boostable   BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order     INT NOT NULL DEFAULT 0,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by     BIGINT,
  updated_by     BIGINT,
  is_active      BOOLEAN NOT NULL DEFAULT TRUE,
  is_deleted     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TRIGGER trg_item_categories_updated_at BEFORE UPDATE ON item_categories
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Module media
CREATE TABLE media_files (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  storage_key VARCHAR(300) NOT NULL UNIQUE,
  mime_type   VARCHAR(50) NOT NULL,
  size_bytes  BIGINT NOT NULL CHECK (size_bytes >= 0),
  width       INT,
  height      INT,
  visibility  VARCHAR(10) NOT NULL DEFAULT 'PUBLIC'
              CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
  status      VARCHAR(10) NOT NULL DEFAULT 'UPLOADED'
              CHECK (status IN ('UPLOADED', 'ATTACHED')),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  BIGINT,
  updated_by  BIGINT,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE,
  is_deleted  BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TRIGGER trg_media_files_updated_at BEFORE UPDATE ON media_files
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Module user / auth (nhóm tiền: wallet; nhóm AI: moderation; nhóm uy tín: reputation)
CREATE TABLE users (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email             VARCHAR(255) NOT NULL,
  password_hash     VARCHAR(100) NOT NULL,
  full_name         VARCHAR(100) NOT NULL,
  phone             VARCHAR(15) NOT NULL,
  area_id           BIGINT REFERENCES areas (id),
  avatar_media_id   BIGINT REFERENCES media_files (id),
  is_admin          BOOLEAN NOT NULL DEFAULT FALSE,
  status            VARCHAR(20) NOT NULL DEFAULT 'PENDING_VERIFICATION'
                    CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'BANNED')),
  otp_code_hash     VARCHAR(100),
  otp_purpose       VARCHAR(20)
                    CHECK (otp_purpose IN ('REGISTER', 'RESET_PASSWORD', 'CHANGE_PHONE')),
  otp_expires_at    TIMESTAMPTZ,
  otp_failed_count  SMALLINT NOT NULL DEFAULT 0,
  pending_phone     VARCHAR(15),
  phone_changed_at  TIMESTAMPTZ,
  credit_balance    BIGINT NOT NULL DEFAULT 0,
  held_credit       BIGINT NOT NULL DEFAULT 0,
  has_topped_up     BOOLEAN NOT NULL DEFAULT FALSE,
  free_ai_used      SMALLINT NOT NULL DEFAULT 0,
  free_ai_date      DATE,
  trust_stars       SMALLINT NOT NULL DEFAULT 5 CHECK (trust_stars BETWEEN 0 AND 5),
  total_points      INT NOT NULL DEFAULT 0,
  rank_level        VARCHAR(10) NOT NULL DEFAULT 'BRONZE'
                    CHECK (rank_level IN ('BRONZE', 'SILVER', 'GOLD', 'DIAMOND')),
  points_today      INT NOT NULL DEFAULT 0,
  points_date       DATE,
  clean_streak      INT NOT NULL DEFAULT 0,
  grace_star_used   BOOLEAN NOT NULL DEFAULT FALSE,
  registration_ip   VARCHAR(45),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by        BIGINT,
  updated_by        BIGINT,
  is_active         BOOLEAN NOT NULL DEFAULT TRUE,
  is_deleted        BOOLEAN NOT NULL DEFAULT FALSE,
  -- tiền không âm
  CONSTRAINT ck_users_credit_non_negative CHECK (credit_balance >= 0 AND held_credit >= 0)
);
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
-- số điện thoại duy nhất trong tài khoản đang hoạt động
CREATE UNIQUE INDEX ux_users_phone_active ON users (phone)
  WHERE status = 'ACTIVE' AND NOT is_deleted;
-- email duy nhất trong các bản ghi chưa xoá mềm
CREATE UNIQUE INDEX ux_users_email ON users (email) WHERE NOT is_deleted;

-- Module audit
CREATE TABLE activity_logs (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  actor_id     BIGINT REFERENCES users (id),
  type         VARCHAR(40) NOT NULL,
  target_type  VARCHAR(20),
  target_id    BIGINT,
  data         JSONB,
  reason       TEXT,
  ip           VARCHAR(45),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_logs_target ON activity_logs (target_type, target_id);

-- Module notification
CREATE TABLE notifications (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users (id),
  type        VARCHAR(40) NOT NULL,
  title       VARCHAR(150) NOT NULL,
  body        TEXT,
  link        VARCHAR(255),
  is_read     BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Module catalog
CREATE TABLE item_attributes (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  category_id  BIGINT NOT NULL REFERENCES item_categories (id),
  name         VARCHAR(100) NOT NULL,
  data_type    VARCHAR(10) NOT NULL
               CHECK (data_type IN ('TEXT', 'NUMBER', 'SELECT', 'BOOLEAN')),
  options      JSONB,
  is_required  BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order   INT NOT NULL DEFAULT 0,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by   BIGINT,
  updated_by   BIGINT,
  is_active    BOOLEAN NOT NULL DEFAULT TRUE,
  is_deleted   BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TRIGGER trg_item_attributes_updated_at BEFORE UPDATE ON item_attributes
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Module item (nhóm phí: listingfee; AI: moderation; duyệt: review)
CREATE TABLE items (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  donor_id             BIGINT NOT NULL REFERENCES users (id),
  category_id          BIGINT NOT NULL REFERENCES item_categories (id),
  area_id              BIGINT NOT NULL REFERENCES areas (id),
  offer_type           VARCHAR(10) NOT NULL CHECK (offer_type IN ('GIVE', 'SWAP')),
  title                VARCHAR(150) NOT NULL,
  description          TEXT NOT NULL,
  desired_item         TEXT,
  condition            VARCHAR(12) NOT NULL
                       CHECK (condition IN ('NEW', 'LIKE_NEW', 'GOOD', 'DEFECTIVE')),
  defect_note          TEXT,
  brand                VARCHAR(80),
  auth_status          VARCHAR(10) NOT NULL DEFAULT 'NONE'
                       CHECK (auth_status IN ('NONE', 'PENDING', 'VERIFIED', 'REJECTED', 'REVOKED')),
  status               VARCHAR(16) NOT NULL DEFAULT 'PENDING_REVIEW'
                       CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'RESERVED', 'TRADED', 'EXPIRED')),
  reject_reason        TEXT,
  post_fee_paid        BOOLEAN NOT NULL DEFAULT FALSE,
  pending_fee_type     VARCHAR(10) CHECK (pending_fee_type IN ('POST', 'EDIT')),
  pending_fee          INT NOT NULL DEFAULT 0 CHECK (pending_fee >= 0),
  fee_state            VARCHAR(10) NOT NULL DEFAULT 'NONE'
                       CHECK (fee_state IN ('NONE', 'HELD', 'CHARGED', 'RELEASED')),
  free_edit_used       BOOLEAN NOT NULL DEFAULT FALSE,
  approved_edit_count  SMALLINT NOT NULL DEFAULT 0,
  ai_risk              VARCHAR(10) NOT NULL DEFAULT 'UNCHECKED'
                       CHECK (ai_risk IN ('LOW', 'MEDIUM', 'HIGH', 'UNCHECKED')),
  ai_result            JSONB,
  review_snapshot      JSONB,
  approved_at          TIMESTAMPTZ,
  expire_at            TIMESTAMPTZ,
  boosted_until        TIMESTAMPTZ,
  view_count           INT NOT NULL DEFAULT 0,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by           BIGINT,
  updated_by           BIGINT,
  is_active            BOOLEAN NOT NULL DEFAULT TRUE,
  is_deleted           BOOLEAN NOT NULL DEFAULT FALSE,
  -- bài Trao đổi bắt buộc có món mong muốn; bài lỗi bắt buộc mô tả lỗi
  CONSTRAINT ck_items_swap_desired CHECK (offer_type <> 'SWAP' OR desired_item IS NOT NULL),
  CONSTRAINT ck_items_defect_note CHECK (condition <> 'DEFECTIVE' OR defect_note IS NOT NULL)
);
CREATE TRIGGER trg_items_updated_at BEFORE UPDATE ON items
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
-- tìm kiếm không dấu
CREATE INDEX ix_items_search ON items
  USING gin (f_unaccent(lower(title || ' ' || description)) gin_trgm_ops);
CREATE INDEX ix_items_list ON items (status, category_id, area_id, created_at DESC)
  WHERE is_active AND NOT is_deleted;
CREATE INDEX ix_items_boost ON items (boosted_until) WHERE boosted_until IS NOT NULL;

-- Module item
CREATE TABLE item_attribute_mappings (
  item_id       BIGINT NOT NULL REFERENCES items (id),
  attribute_id  BIGINT NOT NULL REFERENCES item_attributes (id),
  value         VARCHAR(255) NOT NULL,
  PRIMARY KEY (item_id, attribute_id)
);

-- Module item
CREATE TABLE item_media_mappings (
  item_id     BIGINT NOT NULL REFERENCES items (id),
  media_id    BIGINT NOT NULL REFERENCES media_files (id),
  role        VARCHAR(12) NOT NULL CHECK (role IN ('PHOTO', 'DEFECT', 'AUTH_PROOF')),
  sort_order  SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (item_id, media_id)
);

-- Module request
CREATE TABLE requests (
  id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  item_id                  BIGINT NOT NULL REFERENCES items (id),
  receiver_id              BIGINT NOT NULL REFERENCES users (id),
  offered_item_id          BIGINT REFERENCES items (id),
  type                     VARCHAR(10) NOT NULL CHECK (type IN ('GIVE', 'SWAP')),
  status                   VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN ('PENDING', 'RESERVED', 'AWAITING_LOGISTICS', 'LOGISTICS_CONFIRMED',
                                             'AWAITING_HANDOVER', 'COMPLETED', 'DISPUTED', 'REJECTED', 'CANCELLED')),
  delivery_method          VARCHAR(12) CHECK (delivery_method IN ('IN_PERSON', 'SHIPPING')),
  meeting_place            VARCHAR(255),
  meeting_time             TIMESTAMPTZ,
  donor_waiver_at          TIMESTAMPTZ,
  receiver_waiver_at       TIMESTAMPTZ,
  donor_schedule_ok_at     TIMESTAMPTZ,
  receiver_schedule_ok_at  TIMESTAMPTZ,
  contact_revealed_at      TIMESTAMPTZ,
  donor_handover_at        TIMESTAMPTZ,
  receiver_handover_at     TIMESTAMPTZ,
  as_described             BOOLEAN,
  auto_confirmed           BOOLEAN NOT NULL DEFAULT FALSE,
  chat_violations          SMALLINT NOT NULL DEFAULT 0,
  reserved_at              TIMESTAMPTZ,
  completed_at             TIMESTAMPTZ,
  cancel_reason            VARCHAR(100),
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by               BIGINT,
  updated_by               BIGINT,
  is_active                BOOLEAN NOT NULL DEFAULT TRUE,
  is_deleted               BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TRIGGER trg_requests_updated_at BEFORE UPDATE ON requests
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
-- mỗi người chỉ một yêu cầu đang hoạt động cho một bài
CREATE UNIQUE INDEX ux_requests_active ON requests (item_id, receiver_id)
  WHERE status IN ('PENDING', 'RESERVED', 'AWAITING_LOGISTICS',
                   'LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER', 'DISPUTED');
-- một bài chỉ một giao dịch từ RESERVED trở lên
CREATE UNIQUE INDEX ux_requests_one_reserved ON requests (item_id)
  WHERE status IN ('RESERVED', 'AWAITING_LOGISTICS', 'LOGISTICS_CONFIRMED',
                   'AWAITING_HANDOVER', 'DISPUTED');

-- Module request
CREATE TABLE request_media_mappings (
  request_id   BIGINT NOT NULL REFERENCES requests (id),
  media_id     BIGINT NOT NULL REFERENCES media_files (id),
  stage        VARCHAR(10) NOT NULL CHECK (stage IN ('HANDOVER', 'DISPUTE')),
  uploaded_by  BIGINT NOT NULL REFERENCES users (id),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (request_id, media_id)
);

-- Module chat
CREATE TABLE messages (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id       BIGINT NOT NULL REFERENCES requests (id),
  sender_id        BIGINT REFERENCES users (id),
  kind             VARCHAR(12) NOT NULL
                   CHECK (kind IN ('TEXT', 'IMAGE', 'ITEM_CARD', 'OFFER_CHANGE', 'SYSTEM')),
  content          TEXT,
  media_id         BIGINT REFERENCES media_files (id),
  ref_item_id      BIGINT REFERENCES items (id),
  offer_status     VARCHAR(10) CHECK (offer_status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
  moderation       VARCHAR(8) NOT NULL DEFAULT 'PASS' CHECK (moderation IN ('PASS', 'BLOCKED')),
  moderation_tier  SMALLINT CHECK (moderation_tier IN (1, 2)),
  blocked_reason   VARCHAR(100),
  ai_confidence    NUMERIC(3, 2),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_messages_req ON messages (request_id, id);

-- Module report
CREATE TABLE reports (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  reporter_id     BIGINT NOT NULL REFERENCES users (id),
  target_type     VARCHAR(10) NOT NULL CHECK (target_type IN ('ITEM', 'USER', 'REQUEST')),
  item_id         BIGINT REFERENCES items (id),
  target_user_id  BIGINT REFERENCES users (id),
  request_id      BIGINT REFERENCES requests (id),
  reason          VARCHAR(30) NOT NULL
                  CHECK (reason IN ('NOT_AS_DESCRIBED', 'COUNTERFEIT', 'PROHIBITED_ITEM', 'NO_SHOW',
                                    'WRONG_CONTACT_INFO', 'HARASSMENT', 'OTHER')),
  description     TEXT NOT NULL,
  stage           VARCHAR(8) NOT NULL CHECK (stage IN ('BEFORE', 'AFTER')),
  status          VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                  CHECK (status IN ('OPEN', 'AWAITING_RESPONSE', 'RESOLVED', 'DISMISSED')),
  response        TEXT,
  responded_at    TIMESTAMPTZ,
  resolved_by     BIGINT REFERENCES users (id),
  resolution      TEXT,
  stars_deducted  SMALLINT NOT NULL DEFAULT 0,
  resolved_at     TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by      BIGINT,
  updated_by      BIGINT,
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TRIGGER trg_reports_updated_at BEFORE UPDATE ON reports
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Module wallet
CREATE TABLE payment_orders (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id             BIGINT NOT NULL REFERENCES users (id),
  order_code          VARCHAR(40) NOT NULL UNIQUE,
  amount_vnd          BIGINT NOT NULL CHECK (amount_vnd >= 10000 AND amount_vnd % 1000 = 0),
  credits             INT NOT NULL CHECK (credits >= 0),
  vnd_per_credit      INT NOT NULL,
  provider            VARCHAR(20) NOT NULL,
  provider_txn_id     VARCHAR(60),
  status              VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED')),
  signature_verified  BOOLEAN NOT NULL DEFAULT FALSE,
  callback_data       JSONB,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  paid_at             TIMESTAMPTZ
);
CREATE TRIGGER trg_payment_orders_updated_at BEFORE UPDATE ON payment_orders
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Module wallet
CREATE TABLE credit_ledger (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id           BIGINT NOT NULL REFERENCES users (id),
  amount            INT NOT NULL,
  type              VARCHAR(16) NOT NULL
                    CHECK (type IN ('TOP_UP', 'POST_FEE', 'EDIT_FEE', 'RENEW_FEE', 'BOOST_FEE',
                                    'AI_SEARCH_FEE', 'ADMIN_ADJUST')),
  balance_after     BIGINT NOT NULL CHECK (balance_after >= 0),
  item_id           BIGINT REFERENCES items (id),
  payment_order_id  BIGINT REFERENCES payment_orders (id),
  note              VARCHAR(255),
  created_by        BIGINT REFERENCES users (id),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- chống cộng Credit hai lần cho một đơn nạp
CREATE UNIQUE INDEX ux_ledger_order ON credit_ledger (payment_order_id)
  WHERE payment_order_id IS NOT NULL;
CREATE INDEX ix_ledger_user ON credit_ledger (user_id, created_at DESC);
