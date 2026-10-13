BEGIN;
-- S4-10: cổng đặt hàng dành cho đại lý. Chạy sau sprint4-order-approval.sql.
-- Mỗi hồ sơ đại lý liên kết với tối đa một tài khoản đăng nhập vai trò Đại lý (CUSTOMER); một tài khoản chỉ
-- thuộc một đại lý. Đơn đại lý tự đặt: orders.source='PORTAL', created_by = tài khoản đại lý,
-- trạng thái bắt đầu PENDING_CONFIRM (Chờ nhân viên xác nhận), nhân viên phụ trách xác nhận rồi mới vào bước duyệt.
ALTER TABLE customers ADD COLUMN IF NOT EXISTS portal_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX IF NOT EXISTS customers_portal_user_unique ON customers(portal_user_id) WHERE portal_user_id IS NOT NULL;

-- Đơn từ cổng luôn bắt đầu ở Chờ xác nhận; đơn nhân viên lập luôn bắt đầu ở Nháp.
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='orders_portal_starts_pending') THEN
  ALTER TABLE orders ADD CONSTRAINT orders_portal_starts_pending
   CHECK (status <> 'PENDING_CONFIRM' OR source = 'PORTAL');
 END IF;
END $$;

CREATE INDEX IF NOT EXISTS orders_pending_confirm ON orders(agent_id) WHERE status = 'PENDING_CONFIRM';

-- Quyền orders.self (Đại lý xem/đặt đơn của mình) đã có trong role-permissions.sql; bảo đảm có cho DB cũ.
INSERT INTO role_permissions(role_id, permission_code)
SELECT r.id, 'orders.self' FROM roles r WHERE r.code='CUSTOMER'
ON CONFLICT DO NOTHING;
COMMIT;
