BEGIN;

CREATE TABLE IF NOT EXISTS roles (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS warehouses (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(80) NOT NULL UNIQUE,
  email VARCHAR(255) NOT NULL UNIQUE,
  full_name VARCHAR(150) NOT NULL,
  phone VARCHAR(20),
  password_hash TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  failed_login_attempts INTEGER NOT NULL DEFAULT 0,
  locked_until TIMESTAMP,
  session_version INTEGER NOT NULL DEFAULT 0,
  territory VARCHAR(150),
  lock_reason TEXT,
  requires_password_change BOOLEAN NOT NULL DEFAULT FALSE,
  last_login_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_roles (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_id BIGINT NOT NULL REFERENCES roles(id),
  PRIMARY KEY(user_id, role_id)
);

CREATE TABLE IF NOT EXISTS user_warehouses (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
  PRIMARY KEY(user_id, warehouse_id)
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGSERIAL PRIMARY KEY,
  actor_user_id BIGINT REFERENCES users(id),
  action VARCHAR(100) NOT NULL,
  object_type VARCHAR(80) NOT NULL,
  object_id VARCHAR(80),
  old_value TEXT,
  new_value TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dev_mailbox_messages (
  id BIGSERIAL PRIMARY KEY,
  recipient VARCHAR(180) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  action_url TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dev_mailbox_recipient_created ON dev_mailbox_messages(lower(recipient), created_at DESC);

INSERT INTO roles(code,name) VALUES
 ('ADMIN','Quản trị hệ thống'),('SALES_MANAGER','Quản lý kinh doanh'),('SALES_REP','Nhân viên kinh doanh'),
 ('WH_MANAGER','Quản lý kho'),('WAREHOUSE','Nhân viên kho'),('ACCOUNTANT','Kế toán công nợ'),('CUSTOMER','Đại lý')
ON CONFLICT(code) DO UPDATE SET name=EXCLUDED.name;

INSERT INTO warehouses(code,name) VALUES ('HCM','Kho trung tâm — TP.HCM'),('HN','Kho miền Bắc — Hà Nội'),('DN','Kho miền Trung — Đà Nẵng')
ON CONFLICT(code) DO UPDATE SET name=EXCLUDED.name;

-- Tài khoản chỉ dùng cho môi trường demo. Mật khẩu chung: Demo@123
INSERT INTO users(username,email,full_name,password_hash,territory) VALUES
 ('admin.demo','admin@khoflow.local','Vũ Ngọc Long','/5ZnMgXcciMgWY6/j4gyWyrFaSLVohZLV2WGgnS8DXM=','Toàn hệ thống'),
 ('sales.manager','sales.manager@khoflow.local','Trần Minh Anh','/5ZnMgXcciMgWY6/j4gyWyrFaSLVohZLV2WGgnS8DXM=','Toàn công ty'),
 ('sales.rep','sales@khoflow.local','Nguyễn Hoàng Nam','/5ZnMgXcciMgWY6/j4gyWyrFaSLVohZLV2WGgnS8DXM=','Miền Nam'),
 ('warehouse.manager','warehouse.manager@khoflow.local','Lê Minh Tuấn','/5ZnMgXcciMgWY6/j4gyWyrFaSLVohZLV2WGgnS8DXM=','Kho trung tâm'),
 ('warehouse.staff','warehouse@khoflow.local','Nguyễn Quốc Huy','/5ZnMgXcciMgWY6/j4gyWyrFaSLVohZLV2WGgnS8DXM=','Kho trung tâm'),
 ('accountant.demo','accountant@khoflow.local','Phạm Thu Trang','/5ZnMgXcciMgWY6/j4gyWyrFaSLVohZLV2WGgnS8DXM=','Toàn hệ thống'),
 ('customer.demo','customer@khoflow.local','Đại lý An Bình','/5ZnMgXcciMgWY6/j4gyWyrFaSLVohZLV2WGgnS8DXM=','TP.HCM')
ON CONFLICT(email) DO UPDATE SET full_name=EXCLUDED.full_name;

INSERT INTO user_roles(user_id,role_id)
SELECT u.id,r.id FROM users u JOIN roles r ON
 (u.email='admin@khoflow.local' AND r.code='ADMIN') OR
 (u.email='sales.manager@khoflow.local' AND r.code='SALES_MANAGER') OR
 (u.email='sales@khoflow.local' AND r.code='SALES_REP') OR
 (u.email='warehouse.manager@khoflow.local' AND r.code='WH_MANAGER') OR
 (u.email='warehouse@khoflow.local' AND r.code='WAREHOUSE') OR
 (u.email='accountant@khoflow.local' AND r.code='ACCOUNTANT') OR
 (u.email='customer@khoflow.local' AND r.code='CUSTOMER')
ON CONFLICT DO NOTHING;

INSERT INTO user_warehouses(user_id,warehouse_id)
SELECT u.id,w.id FROM users u CROSS JOIN warehouses w WHERE w.code='HCM' AND u.email IN ('warehouse.manager@khoflow.local','warehouse@khoflow.local')
ON CONFLICT DO NOTHING;

COMMIT;
