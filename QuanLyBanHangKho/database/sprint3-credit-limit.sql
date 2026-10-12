BEGIN;
-- S3-05: hạn mức công nợ và số ngày nợ tối đa của đại lý. Chạy sau sprint3-customers.sql.
-- Hạn mức 0 nghĩa là không cho nợ: đơn phải thanh toán ngay.
ALTER TABLE customers ADD COLUMN IF NOT EXISTS credit_limit NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS credit_days INTEGER NOT NULL DEFAULT 0;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='customers_credit_valid') THEN
  ALTER TABLE customers ADD CONSTRAINT customers_credit_valid
   CHECK (credit_limit >= 0 AND credit_days BETWEEN 0 AND 365);
 END IF;
END $$;

-- Lịch sử thay đổi hạn mức, bắt buộc có lý do.
CREATE TABLE IF NOT EXISTS customer_credit_history (
 id BIGSERIAL PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
 old_limit NUMERIC(18,2) NOT NULL,
 new_limit NUMERIC(18,2) NOT NULL,
 old_days INTEGER NOT NULL,
 new_days INTEGER NOT NULL,
 reason VARCHAR(500) NOT NULL CHECK(length(trim(reason)) > 0),
 changed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
 changed_by_name VARCHAR(150) NOT NULL,
 changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS customer_credit_history_customer ON customer_credit_history(customer_id, changed_at DESC);

DROP TRIGGER IF EXISTS catalog_audit ON customer_credit_history;
CREATE TRIGGER catalog_audit AFTER INSERT OR UPDATE OR DELETE ON customer_credit_history
 FOR EACH ROW EXECUTE FUNCTION sprint2_audit();

-- Chỉ Kế toán công nợ và Quản lý kinh doanh được sửa hạn mức.
INSERT INTO role_permissions(role_id, permission_code)
SELECT r.id, 'customers.credit' FROM roles r WHERE r.code IN ('ACCOUNTANT','SALES_MANAGER')
ON CONFLICT DO NOTHING;
COMMIT;
