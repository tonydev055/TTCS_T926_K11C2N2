BEGIN;
-- S4-03: tồn khả dụng và giữ chỗ tồn cho đơn hàng. Chạy sau sprint4-credit-check.sql.
-- Số lượng tồn tính theo đơn vị cơ bản của sản phẩm (như order_lines.base_quantity).
--
-- Tồn khả dụng = tồn thực tế (on_hand) − tồn đang giữ chỗ cho đơn đã duyệt (reserved).
-- Chỉ đơn Đã duyệt mới giữ chỗ (S4-05). Ràng buộc reserved <= on_hand bảo đảm hai người chốt đơn cùng lúc
-- trên SKU sắp hết không thể làm âm tồn: giao dịch đến sau sẽ chờ khoá dòng rồi thấy số còn lại đúng.
-- Phiếu nhập kho/kiểm kê (Sprint 5) cập nhật on_hand; Sprint 4 chỉ đọc và giữ chỗ.

-- Kho phục vụ của đại lý: tồn khả dụng trên đơn lấy từ kho này.
ALTER TABLE customers ADD COLUMN IF NOT EXISTS warehouse_id BIGINT REFERENCES warehouses(id);
CREATE INDEX IF NOT EXISTS customers_warehouse ON customers(warehouse_id);

CREATE TABLE IF NOT EXISTS inventory (
 id BIGSERIAL PRIMARY KEY,
 warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
 product_id BIGINT NOT NULL REFERENCES products(id),
 on_hand NUMERIC(24,6) NOT NULL DEFAULT 0 CHECK (on_hand >= 0),
 reserved NUMERIC(24,6) NOT NULL DEFAULT 0 CHECK (reserved >= 0),
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE (warehouse_id, product_id),
 CONSTRAINT inventory_reserved_within_on_hand CHECK (reserved <= on_hand)
);

-- Kho đơn được phục vụ, chốt lúc gửi đơn.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS warehouse_id BIGINT REFERENCES warehouses(id);

CREATE TABLE IF NOT EXISTS stock_reservations (
 id BIGSERIAL PRIMARY KEY,
 order_id BIGINT NOT NULL REFERENCES orders(id),
 warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
 product_id BIGINT NOT NULL REFERENCES products(id),
 quantity NUMERIC(24,6) NOT NULL CHECK (quantity > 0),
 status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','RELEASED','CONSUMED')),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 released_at TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS stock_reservations_active
 ON stock_reservations(order_id, warehouse_id, product_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS stock_reservations_stock ON stock_reservations(warehouse_id, product_id) WHERE status = 'ACTIVE';

DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['inventory','stock_reservations'] LOOP
  EXECUTE format('DROP TRIGGER IF EXISTS catalog_audit ON %I', t);
  EXECUTE format('CREATE TRIGGER catalog_audit AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION sprint2_audit()', t);
 END LOOP;
END $$;
COMMIT;
