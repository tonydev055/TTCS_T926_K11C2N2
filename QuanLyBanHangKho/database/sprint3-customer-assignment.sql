BEGIN;
-- S3-06: phân công nhân viên kinh doanh phụ trách đại lý. Chạy sau sprint3-customers.sql.
-- Mỗi đại lý có một người phụ trách chính (customers.sales_rep_id); mọi thay đổi được ghi lịch sử.
CREATE TABLE IF NOT EXISTS customer_assignment_history (
 id BIGSERIAL PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
 from_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
 from_user_name VARCHAR(150),
 to_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
 to_user_name VARCHAR(150),
 reason VARCHAR(500) NOT NULL CHECK(length(trim(reason)) > 0),
 transfer_batch UUID,
 changed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
 changed_by_name VARCHAR(150) NOT NULL,
 changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS customer_assignment_history_customer
 ON customer_assignment_history(customer_id, changed_at DESC);

DROP TRIGGER IF EXISTS catalog_audit ON customer_assignment_history;
CREATE TRIGGER catalog_audit AFTER INSERT OR UPDATE OR DELETE ON customer_assignment_history
 FOR EACH ROW EXECUTE FUNCTION sprint2_audit();

-- Quản lý kinh doanh phân công và chuyển giao địa bàn.
INSERT INTO role_permissions(role_id, permission_code)
SELECT r.id, 'customers.assign' FROM roles r WHERE r.code='SALES_MANAGER'
ON CONFLICT DO NOTHING;
COMMIT;
