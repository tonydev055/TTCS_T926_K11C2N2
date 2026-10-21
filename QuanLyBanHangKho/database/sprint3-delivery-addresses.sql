BEGIN;
-- S3-04: nhiều điểm giao hàng cho một đại lý. Chạy sau sprint3-customers.sql.
CREATE TABLE IF NOT EXISTS customer_addresses (
 id BIGSERIAL PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
 address VARCHAR(500) NOT NULL CHECK(length(trim(address)) > 0),
 recipient_name VARCHAR(150) NOT NULL CHECK(length(trim(recipient_name)) > 0),
 phone VARCHAR(20) NOT NULL CHECK(length(trim(phone)) > 0),
 directions VARCHAR(1000) NOT NULL DEFAULT '',
 is_default BOOLEAN NOT NULL DEFAULT FALSE,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(active OR NOT is_default),
 UNIQUE(id, customer_id)
);
-- Mỗi đại lý có tối đa một điểm giao mặc định đang dùng.
CREATE UNIQUE INDEX IF NOT EXISTS customer_addresses_one_default
 ON customer_addresses(customer_id) WHERE is_default AND active;
CREATE INDEX IF NOT EXISTS customer_addresses_customer ON customer_addresses(customer_id) WHERE active;

-- Đơn hàng chỉ được chọn điểm giao thuộc đúng đại lý của đơn.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_address_id BIGINT;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='orders_delivery_address_of_agent') THEN
  ALTER TABLE orders ADD CONSTRAINT orders_delivery_address_of_agent
   FOREIGN KEY (delivery_address_id, agent_id) REFERENCES customer_addresses(id, customer_id);
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='orders_delivery_address_needs_agent') THEN
  ALTER TABLE orders ADD CONSTRAINT orders_delivery_address_needs_agent
   CHECK (delivery_address_id IS NULL OR agent_id IS NOT NULL);
 END IF;
END $$;

DROP TRIGGER IF EXISTS catalog_audit ON customer_addresses;
CREATE TRIGGER catalog_audit AFTER INSERT OR UPDATE OR DELETE ON customer_addresses
 FOR EACH ROW EXECUTE FUNCTION sprint2_audit();
COMMIT;
