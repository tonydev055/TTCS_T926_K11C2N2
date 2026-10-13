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
CREATE OR REPLACE FUNCTION transfer_final_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.status='RECEIVED' THEN RAISE EXCEPTION 'Phiếu chuyển đã nhận không được sửa hoặc xoá' USING ERRCODE='P0001'; END IF;
 IF TG_OP='DELETE' AND OLD.status<>'DRAFT' THEN RAISE EXCEPTION 'Phiếu đã xuất chuyển không được xoá' USING ERRCODE='P0001'; END IF;
 IF TG_OP='UPDATE' AND OLD.status='IN_TRANSIT' AND (NEW.source_warehouse_id<>OLD.source_warehouse_id OR NEW.destination_warehouse_id<>OLD.destination_warehouse_id)
 THEN RAISE EXCEPTION 'Không được đổi kho của phiếu đang chuyển' USING ERRCODE='P0001'; END IF;
 RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
DROP TRIGGER IF EXISTS transfer_final_guard ON stock_transfers;
CREATE TRIGGER transfer_final_guard BEFORE UPDATE OR DELETE ON stock_transfers FOR EACH ROW EXECUTE FUNCTION transfer_final_guard();
COMMIT;

