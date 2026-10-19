BEGIN;
-- S8-01: phiếu trả hàng từ đại lý theo hoá đơn gốc; Quản lý kho xác nhận nhận hàng mới cộng tồn.
-- Chạy sau các migration Sprint 6 và sprint7-sales-invoices.sql. Có thể chạy lại an toàn.

-- Kho hàng lỗi: nhận hàng trả bị hỏng, không dùng làm kho phục vụ đại lý.
ALTER TABLE warehouses ADD COLUMN IF NOT EXISTS is_defect BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO invoice_number_counters(document_type,last_number) VALUES('RETURN',0)
ON CONFLICT (document_type) DO NOTHING;

CREATE TABLE IF NOT EXISTS sales_returns(
 id BIGSERIAL PRIMARY KEY,
 code VARCHAR(40) NOT NULL UNIQUE,
 invoice_id BIGINT NOT NULL REFERENCES invoices(id),
 customer_id BIGINT NOT NULL REFERENCES customers(id),
 warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
 defect_warehouse_id BIGINT REFERENCES warehouses(id),
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN('PENDING','RECEIVED','REJECTED','CANCELLED')),
 note VARCHAR(1000) NOT NULL DEFAULT '',
 total_amount NUMERIC(18,2) NOT NULL CHECK(total_amount>=0),
 created_by BIGINT NOT NULL REFERENCES users(id),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 decided_by BIGINT REFERENCES users(id),
 decided_at TIMESTAMP,
 decision_note VARCHAR(1000) NOT NULL DEFAULT '',
 CHECK(status='PENDING' OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
 CHECK(status NOT IN('REJECTED','CANCELLED') OR length(btrim(decision_note))>0)
);

CREATE TABLE IF NOT EXISTS sales_return_lines(
 id BIGSERIAL PRIMARY KEY,
 return_id BIGINT NOT NULL REFERENCES sales_returns(id),
 invoice_line_id BIGINT NOT NULL REFERENCES invoice_lines(id),
 order_line_id BIGINT NOT NULL REFERENCES order_lines(id),
 product_id BIGINT NOT NULL REFERENCES products(id),
 unit_id BIGINT NOT NULL REFERENCES product_units(id),
 quantity NUMERIC(24,6) NOT NULL CHECK(quantity>0),
 conversion_factor NUMERIC(18,6) NOT NULL CHECK(conversion_factor>0),
 base_quantity NUMERIC(24,6) NOT NULL CHECK(base_quantity>0),
 unit_price NUMERIC(18,2) NOT NULL CHECK(unit_price>=0),
 amount NUMERIC(18,2) NOT NULL CHECK(amount>=0),
 reason VARCHAR(20) NOT NULL CHECK(reason IN('DAMAGED','EXPIRED','WRONG_ITEM','QUALITY','CUSTOMER_REQUEST','OTHER')),
 reason_note VARCHAR(500) NOT NULL DEFAULT '',
 condition VARCHAR(10) CHECK(condition IS NULL OR condition IN('GOOD','DEFECT')),
 UNIQUE(return_id,invoice_line_id),
 CHECK(reason<>'OTHER' OR length(btrim(reason_note))>0)
);

-- Hàng trả được nhập vào lô/vị trí nào: lô gốc đã xuất (hàng tốt) hoặc cùng số lô trong kho hàng lỗi.
CREATE TABLE IF NOT EXISTS sales_return_allocations(
 id BIGSERIAL PRIMARY KEY,
 return_line_id BIGINT NOT NULL REFERENCES sales_return_lines(id),
 source_bin_id BIGINT NOT NULL REFERENCES stock_bins(id),
 target_bin_id BIGINT NOT NULL REFERENCES stock_bins(id),
 warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
 base_quantity NUMERIC(24,6) NOT NULL CHECK(base_quantity>0),
 UNIQUE(return_line_id,source_bin_id)
);

CREATE INDEX IF NOT EXISTS sales_returns_invoice ON sales_returns(invoice_id,status);
CREATE INDEX IF NOT EXISTS sales_returns_customer ON sales_returns(customer_id,created_at DESC);
CREATE INDEX IF NOT EXISTS sales_return_lines_invoice_line ON sales_return_lines(invoice_line_id);
CREATE INDEX IF NOT EXISTS sales_return_allocations_source ON sales_return_allocations(source_bin_id);

-- Phiếu trả là chứng từ: không xoá; dòng và phân bổ không sửa sau khi ghi.
CREATE OR REPLACE FUNCTION sprint8_return_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Phiếu trả hàng là chứng từ, không được xoá' USING ERRCODE='P0001'; END IF;
 IF TG_TABLE_NAME='sales_returns' THEN
  IF OLD.status<>'PENDING' THEN RAISE EXCEPTION 'Phiếu trả hàng đã xử lý, không được sửa' USING ERRCODE='P0001'; END IF;
  IF NEW.invoice_id<>OLD.invoice_id OR NEW.customer_id<>OLD.customer_id OR NEW.total_amount<>OLD.total_amount OR NEW.created_by<>OLD.created_by THEN
   RAISE EXCEPTION 'Không được đổi hoá đơn, đại lý hoặc giá trị của phiếu trả' USING ERRCODE='P0001';
  END IF;
 ELSIF TG_TABLE_NAME='sales_return_lines' THEN
  IF (SELECT status FROM sales_returns WHERE id=OLD.return_id)<>'PENDING'
     OR NEW.quantity<>OLD.quantity OR NEW.amount<>OLD.amount OR NEW.invoice_line_id<>OLD.invoice_line_id THEN
   RAISE EXCEPTION 'Dòng phiếu trả không được sửa' USING ERRCODE='P0001';
  END IF;
 ELSE
  RAISE EXCEPTION 'Phân bổ hàng trả không được sửa' USING ERRCODE='P0001';
 END IF;
 RETURN NEW;
END $$;
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['sales_returns','sales_return_lines','sales_return_allocations'] LOOP
  EXECUTE format('DROP TRIGGER IF EXISTS sales_return_guard ON %I',t);
  EXECUTE format('CREATE TRIGGER sales_return_guard BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION sprint8_return_guard()',t);
  EXECUTE format('DROP TRIGGER IF EXISTS catalog_audit ON %I',t);
  EXECUTE format('CREATE TRIGGER catalog_audit AFTER INSERT OR UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION sprint2_audit()',t);
 END LOOP;
END $$;
COMMIT;
