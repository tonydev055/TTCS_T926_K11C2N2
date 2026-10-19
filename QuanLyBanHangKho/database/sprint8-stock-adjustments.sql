BEGIN;
-- S8-03: phiếu điều chỉnh giảm tồn (hàng hỏng, mất, hết hạn phải huỷ) có phê duyệt.
-- Chạy sau các migration Sprint 5/6 (stock_bins, stock_movements) và sprint7-sales-invoices.sql (bộ đếm số chứng từ). Có thể chạy lại an toàn.
INSERT INTO invoice_number_counters(document_type,last_number) VALUES('STOCK_ADJ',0)
ON CONFLICT (document_type) DO NOTHING;

CREATE TABLE IF NOT EXISTS stock_adjustments(
 id BIGSERIAL PRIMARY KEY,
 code VARCHAR(40) NOT NULL UNIQUE,
 warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
 reason VARCHAR(20) NOT NULL CHECK(reason IN('DAMAGED','LOST','EXPIRED_DISPOSAL')),
 note VARCHAR(1000) NOT NULL DEFAULT '',
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN('PENDING','APPROVED','REJECTED','CANCELLED')),
 total_loss NUMERIC(18,2) NOT NULL CHECK(total_loss>=0),
 created_by BIGINT NOT NULL REFERENCES users(id),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 decided_by BIGINT REFERENCES users(id),
 decided_at TIMESTAMP,
 decision_note VARCHAR(1000) NOT NULL DEFAULT '',
 CHECK(status='PENDING' OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
 -- Người lập và người duyệt không được là một (áp dụng cả duyệt lẫn từ chối).
 CHECK(status NOT IN('APPROVED','REJECTED') OR decided_by<>created_by),
 CHECK(status NOT IN('REJECTED','CANCELLED') OR length(btrim(decision_note))>0)
);

CREATE TABLE IF NOT EXISTS stock_adjustment_lines(
 id BIGSERIAL PRIMARY KEY,
 adjustment_id BIGINT NOT NULL REFERENCES stock_adjustments(id),
 product_id BIGINT NOT NULL REFERENCES products(id),
 bin_id BIGINT NOT NULL REFERENCES stock_bins(id),
 quantity NUMERIC(24,6) NOT NULL CHECK(quantity>0),
 loss_value NUMERIC(18,2) NOT NULL CHECK(loss_value>=0),
 note VARCHAR(500) NOT NULL DEFAULT '',
 UNIQUE(adjustment_id,bin_id)
);
CREATE INDEX IF NOT EXISTS stock_adjustments_warehouse ON stock_adjustments(warehouse_id,status,created_at DESC);

CREATE OR REPLACE FUNCTION sprint8_stock_adjustment_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Phiếu điều chỉnh tồn là chứng từ, không được xoá' USING ERRCODE='P0001'; END IF;
 IF TG_TABLE_NAME='stock_adjustments' THEN
  IF OLD.status<>'PENDING' THEN RAISE EXCEPTION 'Phiếu điều chỉnh tồn đã xử lý, không được sửa' USING ERRCODE='P0001'; END IF;
  IF NEW.warehouse_id<>OLD.warehouse_id OR NEW.total_loss<>OLD.total_loss OR NEW.created_by<>OLD.created_by OR NEW.reason<>OLD.reason THEN
   RAISE EXCEPTION 'Không được đổi kho, lý do hoặc giá trị tổn thất của phiếu điều chỉnh' USING ERRCODE='P0001';
  END IF;
 ELSE
  RAISE EXCEPTION 'Dòng phiếu điều chỉnh tồn không được sửa' USING ERRCODE='P0001';
 END IF;
 RETURN NEW;
END $$;
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['stock_adjustments','stock_adjustment_lines'] LOOP
  EXECUTE format('DROP TRIGGER IF EXISTS stock_adjustment_guard ON %I',t);
  EXECUTE format('CREATE TRIGGER stock_adjustment_guard BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION sprint8_stock_adjustment_guard()',t);
  EXECUTE format('DROP TRIGGER IF EXISTS catalog_audit ON %I',t);
  EXECUTE format('CREATE TRIGGER catalog_audit AFTER INSERT OR UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION sprint2_audit()',t);
 END LOOP;
END $$;
COMMIT;
