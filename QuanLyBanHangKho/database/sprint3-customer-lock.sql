BEGIN;
-- S3-07: khoá hoặc mở giao dịch với đại lý. Chạy sau sprint3-customers.sql.
ALTER TABLE customers ADD COLUMN IF NOT EXISTS trading_locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS lock_reason VARCHAR(500);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS locked_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='customers_lock_reason_required') THEN
  ALTER TABLE customers ADD CONSTRAINT customers_lock_reason_required
   CHECK (NOT trading_locked OR length(trim(coalesce(lock_reason,''))) > 0);
 END IF;
END $$;

CREATE TABLE IF NOT EXISTS customer_lock_history (
 id BIGSERIAL PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
 action VARCHAR(10) NOT NULL CHECK(action IN ('LOCK','UNLOCK')),
 reason VARCHAR(500) NOT NULL DEFAULT '',
 changed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
 changed_by_name VARCHAR(150) NOT NULL,
 changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(action <> 'LOCK' OR length(trim(reason)) > 0)
);
CREATE INDEX IF NOT EXISTS customer_lock_history_customer ON customer_lock_history(customer_id, changed_at DESC);
DROP TRIGGER IF EXISTS catalog_audit ON customer_lock_history;
CREATE TRIGGER catalog_audit AFTER INSERT OR UPDATE OR DELETE ON customer_lock_history
 FOR EACH ROW EXECUTE FUNCTION sprint2_audit();

-- Chặn tạo đơn mới cho đại lý bị khoá giao dịch hoặc ngừng giao dịch, ở mọi kênh (nhân viên hay cổng đại lý).
-- Chỉ áp dụng khi INSERT: đơn đang dở vẫn được cập nhật và xử lý tiếp.
CREATE OR REPLACE FUNCTION block_orders_for_locked_customer() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE k RECORD;
BEGIN
 IF NEW.agent_id IS NULL THEN RETURN NEW; END IF;
 SELECT code, status, trading_locked, lock_reason INTO k FROM customers WHERE id=NEW.agent_id;
 IF k.trading_locked THEN
  RAISE EXCEPTION 'Đại lý % đang bị khoá giao dịch (%), không thể tạo đơn mới', k.code, k.lock_reason
   USING ERRCODE='P0001';
 END IF;
 IF k.status <> 'ACTIVE' THEN
  RAISE EXCEPTION 'Đại lý % đã ngừng giao dịch, không thể tạo đơn mới', k.code USING ERRCODE='P0001';
 END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS orders_block_locked_customer ON orders;
CREATE TRIGGER orders_block_locked_customer BEFORE INSERT ON orders
 FOR EACH ROW EXECUTE FUNCTION block_orders_for_locked_customer();

INSERT INTO role_permissions(role_id, permission_code)
SELECT r.id, 'customers.lock' FROM roles r WHERE r.code IN ('ACCOUNTANT','SALES_MANAGER')
ON CONFLICT DO NOTHING;
COMMIT;
