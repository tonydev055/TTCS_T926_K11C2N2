BEGIN;
-- S5-04, phụ thuộc S5-03 (warehouse_locations).
ALTER TABLE products ADD COLUMN IF NOT EXISTS lot_managed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE stock_receipts ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','POSTED'));
ALTER TABLE stock_receipts ADD COLUMN IF NOT EXISTS document_number VARCHAR(80) NOT NULL DEFAULT '';
ALTER TABLE stock_receipts ADD COLUMN IF NOT EXISTS receipt_date DATE NOT NULL DEFAULT CURRENT_DATE;
ALTER TABLE stock_receipts ADD COLUMN IF NOT EXISTS note VARCHAR(1000) NOT NULL DEFAULT '';
ALTER TABLE stock_receipts ADD COLUMN IF NOT EXISTS direction SMALLINT NOT NULL DEFAULT 1 CHECK(direction IN (-1,1));
ALTER TABLE stock_receipts ADD COLUMN IF NOT EXISTS source_receipt_id BIGINT REFERENCES stock_receipts(id);
ALTER TABLE stock_receipts ADD COLUMN IF NOT EXISTS created_by BIGINT REFERENCES users(id);
ALTER TABLE stock_receipts ADD COLUMN IF NOT EXISTS posted_at TIMESTAMP;
ALTER TABLE stock_receipt_lines ADD COLUMN IF NOT EXISTS location_id BIGINT REFERENCES warehouse_locations(id);
ALTER TABLE stock_receipt_lines ADD COLUMN IF NOT EXISTS lot_number VARCHAR(80) NOT NULL DEFAULT '';
ALTER TABLE stock_receipt_lines ADD COLUMN IF NOT EXISTS expiry_date DATE;
CREATE TABLE IF NOT EXISTS stock_bins(
 id BIGSERIAL PRIMARY KEY,warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
 location_id BIGINT,product_id BIGINT NOT NULL REFERENCES products(id),
 lot_number VARCHAR(80) NOT NULL DEFAULT '',expiry_date DATE,
 on_hand NUMERIC(24,6) NOT NULL DEFAULT 0 CHECK(on_hand>=0),
 FOREIGN KEY(location_id,warehouse_id) REFERENCES warehouse_locations(id,warehouse_id),
 UNIQUE NULLS NOT DISTINCT(warehouse_id,location_id,product_id,lot_number,expiry_date)
);
-- Chuyển tồn có trước sang khu chưa phân vị trí, giữ nguyên tổng tồn, không thêm tồn mẫu.
INSERT INTO stock_bins(warehouse_id,product_id,on_hand)
SELECT i.warehouse_id,i.product_id,i.on_hand FROM inventory i
WHERE NOT EXISTS(SELECT 1 FROM stock_bins b WHERE b.warehouse_id=i.warehouse_id AND b.product_id=i.product_id);
CREATE TABLE IF NOT EXISTS stock_movements(
 id BIGSERIAL PRIMARY KEY,warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
 product_id BIGINT NOT NULL REFERENCES products(id),bin_id BIGINT REFERENCES stock_bins(id),
 delta NUMERIC(24,6) NOT NULL,kind VARCHAR(30) NOT NULL,document_id BIGINT NOT NULL,
 line_id BIGINT NOT NULL,actor_id BIGINT REFERENCES users(id),reason VARCHAR(1000) NOT NULL DEFAULT '',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(kind,document_id,line_id,warehouse_id)
);
CREATE INDEX IF NOT EXISTS stock_movements_lookup ON stock_movements(warehouse_id,product_id,created_at DESC);
CREATE OR REPLACE FUNCTION stock_receipt_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='stock_receipts' THEN
  IF OLD.status='POSTED' THEN RAISE EXCEPTION 'Phiếu đã xác nhận không được sửa hoặc xoá; hãy lập phiếu điều chỉnh' USING ERRCODE='P0001'; END IF;
 ELSE
  IF EXISTS(SELECT 1 FROM stock_receipts WHERE id=COALESCE(OLD.receipt_id,NEW.receipt_id) AND status='POSTED')
  THEN RAISE EXCEPTION 'Dòng phiếu đã xác nhận không được sửa hoặc xoá' USING ERRCODE='P0001'; END IF;
 END IF;
 RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
DROP TRIGGER IF EXISTS stock_receipt_immutable ON stock_receipts;
CREATE TRIGGER stock_receipt_immutable BEFORE UPDATE OR DELETE ON stock_receipts FOR EACH ROW EXECUTE FUNCTION stock_receipt_guard();
DROP TRIGGER IF EXISTS stock_receipt_line_immutable ON stock_receipt_lines;
CREATE TRIGGER stock_receipt_line_immutable BEFORE INSERT OR UPDATE OR DELETE ON stock_receipt_lines FOR EACH ROW EXECUTE FUNCTION stock_receipt_guard();
COMMIT;
