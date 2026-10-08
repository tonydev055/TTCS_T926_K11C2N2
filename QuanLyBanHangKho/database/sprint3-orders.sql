-- S3-09: tạo đơn hàng cho một đại lý (lưu nháp). Chạy sau sprint1.sql, sprint2.sql và sprint3.sql.
-- An toàn khi chạy lại nhiều lần.
BEGIN;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS created_by BIGINT REFERENCES users(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_address TEXT NOT NULL DEFAULT '';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS desired_delivery_date DATE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS note TEXT NOT NULL DEFAULT '';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS subtotal NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_total NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payable NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Giá và chiết khấu được chốt tại thời điểm lưu (theo đơn vị cơ bản của sản phẩm).
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS base_price NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS line_total NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS policy_id BIGINT REFERENCES discount_policies(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS orders_created_by_status ON orders(created_by, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS order_lines_order ON order_lines(order_id);

COMMIT;
