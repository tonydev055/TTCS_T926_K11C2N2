BEGIN;
-- S5-07, phụ thuộc S5-03 và S5-04.
CREATE TABLE IF NOT EXISTS stock_transfers(
 id BIGSERIAL PRIMARY KEY,code VARCHAR(80) NOT NULL UNIQUE,
 source_warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),destination_warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','IN_TRANSIT','RECEIVED')),
 note VARCHAR(1000) NOT NULL DEFAULT '',receive_reason VARCHAR(1000) NOT NULL DEFAULT '',
 created_by BIGINT REFERENCES users(id),created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 sent_at TIMESTAMP,received_at TIMESTAMP,CHECK(source_warehouse_id<>destination_warehouse_id)
);
CREATE TABLE IF NOT EXISTS stock_transfer_lines(
 id BIGSERIAL PRIMARY KEY,transfer_id BIGINT NOT NULL REFERENCES stock_transfers(id),
 source_bin_id BIGINT NOT NULL REFERENCES stock_bins(id),product_id BIGINT NOT NULL REFERENCES products(id),
 unit_id BIGINT NOT NULL REFERENCES product_units(id),quantity NUMERIC(18,6) NOT NULL CHECK(quantity>0),
 factor NUMERIC(18,6) NOT NULL CHECK(factor>0),base_quantity NUMERIC(24,6) GENERATED ALWAYS AS(quantity*factor) STORED,
 received_quantity NUMERIC(18,6) CHECK(received_quantity>=0),destination_location_id BIGINT REFERENCES warehouse_locations(id)
);
ALTER TABLE stock_transfers DROP CONSTRAINT IF EXISTS stock_transfers_status_check;
ALTER TABLE stock_transfers ADD CONSTRAINT stock_transfers_status_check CHECK(status IN ('DRAFT','IN_TRANSIT','RECEIVED','CANCELLED'));
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS cancelled_by BIGINT REFERENCES users(id);
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(1000);
CREATE OR REPLACE FUNCTION transfer_final_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.status IN ('RECEIVED','CANCELLED') THEN RAISE EXCEPTION 'Phiếu chuyển đã nhận hoặc hủy không được sửa hoặc xoá' USING ERRCODE='P0001'; END IF;
 IF TG_OP='UPDATE' AND NEW.status='CANCELLED' AND (OLD.status<>'DRAFT' OR btrim(COALESCE(NEW.cancel_reason,''))='') THEN RAISE EXCEPTION 'Chỉ hủy phiếu nháp và phải có lý do' USING ERRCODE='P0001'; END IF;
 IF TG_OP='DELETE' AND OLD.status<>'DRAFT' THEN RAISE EXCEPTION 'Phiếu đã xuất chuyển không được xoá' USING ERRCODE='P0001'; END IF;
 IF TG_OP='UPDATE' AND OLD.status='IN_TRANSIT' AND (NEW.source_warehouse_id<>OLD.source_warehouse_id OR NEW.destination_warehouse_id<>OLD.destination_warehouse_id)
 THEN RAISE EXCEPTION 'Không được đổi kho của phiếu đang chuyển' USING ERRCODE='P0001'; END IF;
 RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
DROP TRIGGER IF EXISTS transfer_final_guard ON stock_transfers;
CREATE TRIGGER transfer_final_guard BEFORE UPDATE OR DELETE ON stock_transfers FOR EACH ROW EXECUTE FUNCTION transfer_final_guard();
-- Dòng phiếu: chỉ sửa tự do khi Nháp; đang chuyển chỉ ghi nhận số nhận (không vượt số gửi); đã nhận thì khoá.
CREATE OR REPLACE FUNCTION transfer_line_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s VARCHAR(16);
BEGIN
 SELECT status INTO s FROM stock_transfers WHERE id=CASE WHEN TG_OP='INSERT' THEN NEW.transfer_id ELSE OLD.transfer_id END FOR UPDATE;
 IF s='DRAFT' AND (TG_OP<>'UPDATE' OR NEW.transfer_id=OLD.transfer_id) THEN RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END; END IF;
 IF s='IN_TRANSIT' AND TG_OP='UPDATE' AND NEW.transfer_id=OLD.transfer_id AND NEW.source_bin_id=OLD.source_bin_id AND NEW.product_id=OLD.product_id
    AND NEW.unit_id=OLD.unit_id AND NEW.quantity=OLD.quantity AND NEW.factor=OLD.factor THEN
  IF NEW.received_quantity>NEW.quantity THEN RAISE EXCEPTION 'Số nhận không được vượt số đã xuất chuyển' USING ERRCODE='P0001'; END IF;
  RETURN NEW;
 END IF;
 RAISE EXCEPTION 'Dòng phiếu chuyển đã xuất hoặc đã nhận không được sửa hoặc xoá' USING ERRCODE='P0001';
END $$;
DROP TRIGGER IF EXISTS transfer_line_guard ON stock_transfer_lines;
CREATE TRIGGER transfer_line_guard BEFORE INSERT OR UPDATE OR DELETE ON stock_transfer_lines FOR EACH ROW EXECUTE FUNCTION transfer_line_guard();
COMMIT;

