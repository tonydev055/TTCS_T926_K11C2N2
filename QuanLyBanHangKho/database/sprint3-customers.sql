BEGIN;
-- S3-03: hồ sơ đại lý. Chạy sau sprint2.sql, role-permissions.sql và sprint3.sql.
-- Nhóm khách hàng của đại lý quyết định bảng giá được áp dụng (price_lists.customer_group_id).
CREATE TABLE IF NOT EXISTS customers (
 id BIGSERIAL PRIMARY KEY,
 code VARCHAR(40) NOT NULL CHECK(code ~ '^[A-Z0-9][A-Z0-9_.-]{0,39}$'),
 name VARCHAR(200) NOT NULL CHECK(length(trim(name)) > 0),
 tax_code VARCHAR(14) NOT NULL DEFAULT '' CHECK(tax_code = '' OR tax_code ~ '^[0-9]{10}(-[0-9]{3})?$'),
 customer_group_id BIGINT NOT NULL REFERENCES customer_groups(id),
 region VARCHAR(150) NOT NULL CHECK(length(trim(region)) > 0),
 phone VARCHAR(20) NOT NULL DEFAULT '',
 sales_rep_id BIGINT REFERENCES users(id),
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','INACTIVE')),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Mã đại lý là duy nhất (luôn lưu chữ in hoa).
CREATE UNIQUE INDEX IF NOT EXISTS customers_code_unique ON customers(code);
CREATE INDEX IF NOT EXISTS customers_sales_rep ON customers(sales_rep_id);
CREATE INDEX IF NOT EXISTS customers_group ON customers(customer_group_id);

-- Đơn hàng gắn với đại lý. orders.customer_id hiện là tài khoản người đặt (users).
ALTER TABLE orders ADD COLUMN IF NOT EXISTS agent_id BIGINT REFERENCES customers(id);
CREATE INDEX IF NOT EXISTS orders_agent ON orders(agent_id);

DROP TRIGGER IF EXISTS catalog_audit ON customers;
CREATE TRIGGER catalog_audit AFTER INSERT OR UPDATE OR DELETE ON customers
 FOR EACH ROW EXECUTE FUNCTION sprint2_audit();

-- Quyền mới: khai báo và sửa hồ sơ đại lý.
INSERT INTO role_permissions(role_id, permission_code)
SELECT r.id, 'customers.write' FROM roles r WHERE r.code IN ('ACCOUNTANT','SALES_MANAGER')
ON CONFLICT DO NOTHING;
COMMIT;
