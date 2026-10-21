BEGIN;
-- S5-08, phụ thuộc S5-03/04.
CREATE TABLE IF NOT EXISTS stock_counts(
 id BIGSERIAL PRIMARY KEY,code VARCHAR(80) NOT NULL UNIQUE,warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
 category_id BIGINT REFERENCES categories(id),status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','POSTED')),
 reason VARCHAR(1000) NOT NULL DEFAULT '',created_by BIGINT REFERENCES users(id),posted_by BIGINT REFERENCES users(id),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,posted_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS stock_count_lines(
 id BIGSERIAL PRIMARY KEY,count_id BIGINT NOT NULL REFERENCES stock_counts(id),bin_id BIGINT NOT NULL REFERENCES stock_bins(id),
 product_id BIGINT NOT NULL REFERENCES products(id),snapshot_quantity NUMERIC(24,6) NOT NULL CHECK(snapshot_quantity>=0),
 actual_quantity NUMERIC(24,6) CHECK(actual_quantity>=0),UNIQUE(count_id,bin_id)
);
ALTER TABLE stock_counts DROP CONSTRAINT IF EXISTS stock_counts_status_check;
ALTER TABLE stock_counts ADD CONSTRAINT stock_counts_status_check CHECK(status IN ('DRAFT','POSTED','CANCELLED'));
ALTER TABLE stock_counts ADD COLUMN IF NOT EXISTS cancelled_by BIGINT REFERENCES users(id);
ALTER TABLE stock_counts ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
ALTER TABLE stock_counts ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(1000);
CREATE OR REPLACE FUNCTION count_final_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='stock_counts' THEN
  IF OLD.status IN ('POSTED','CANCELLED') THEN RAISE EXCEPTION 'Phiếu kiểm kê đã chốt hoặc hủy không được sửa/xoá' USING ERRCODE='P0001'; END IF;
  IF TG_OP='UPDATE' AND NEW.status='CANCELLED' AND (OLD.status<>'DRAFT' OR btrim(COALESCE(NEW.cancel_reason,''))='') THEN RAISE EXCEPTION 'Chỉ hủy phiếu nháp và phải có lý do' USING ERRCODE='P0001'; END IF;
 ELSE
  PERFORM 1 FROM stock_counts WHERE id=COALESCE(OLD.count_id,NEW.count_id) FOR UPDATE;
  IF EXISTS(SELECT 1 FROM stock_counts WHERE id=COALESCE(OLD.count_id,NEW.count_id) AND status IN ('POSTED','CANCELLED'))
  THEN RAISE EXCEPTION 'Dòng kiểm kê đã chốt không được sửa/xoá' USING ERRCODE='P0001'; END IF;
 END IF;
 RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
DROP TRIGGER IF EXISTS count_final_guard ON stock_counts;
CREATE TRIGGER count_final_guard BEFORE UPDATE OR DELETE ON stock_counts FOR EACH ROW EXECUTE FUNCTION count_final_guard();
DROP TRIGGER IF EXISTS count_line_final_guard ON stock_count_lines;
CREATE TRIGGER count_line_final_guard BEFORE INSERT OR UPDATE OR DELETE ON stock_count_lines FOR EACH ROW EXECUTE FUNCTION count_final_guard();
COMMIT;

