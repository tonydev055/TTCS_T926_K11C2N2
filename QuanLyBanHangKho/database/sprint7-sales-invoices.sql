-- S7-01: phát hành hóa đơn từ số lượng thực giao.
-- Số chứng từ dùng bộ đếm theo giao dịch (không dùng sequence) để rollback không làm nhảy số.
BEGIN;

CREATE TABLE IF NOT EXISTS invoice_number_counters (
    document_type VARCHAR(20) PRIMARY KEY,
    last_number BIGINT NOT NULL DEFAULT 0 CHECK (last_number >= 0)
);
INSERT INTO invoice_number_counters(document_type,last_number)
VALUES ('INVOICE',0),('ADJUSTMENT',0)
ON CONFLICT (document_type) DO NOTHING;

CREATE TABLE IF NOT EXISTS invoices (
    id BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(40) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    period_from DATE NOT NULL,
    period_to DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED' CHECK (status='ISSUED'),
    subtotal NUMERIC(18,2) NOT NULL CHECK (subtotal>=0),
    discount_total NUMERIC(18,2) NOT NULL CHECK (discount_total>=0),
    total_amount NUMERIC(18,2) NOT NULL CHECK (total_amount>=0),
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    issued_by BIGINT NOT NULL REFERENCES users(id),
    note VARCHAR(1000) NOT NULL DEFAULT '',
    CHECK (period_to>=period_from),
    CHECK (subtotal-discount_total=total_amount)
);

CREATE TABLE IF NOT EXISTS invoice_deliveries (
    invoice_id BIGINT NOT NULL REFERENCES invoices(id),
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id),
    delivery_confirmation_id BIGINT NOT NULL UNIQUE REFERENCES delivery_confirmations(id),
    PRIMARY KEY(invoice_id,order_id)
);

CREATE TABLE IF NOT EXISTS invoice_lines (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id),
    order_id BIGINT NOT NULL REFERENCES orders(id),
    order_line_id BIGINT NOT NULL UNIQUE REFERENCES order_lines(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    unit_id BIGINT NOT NULL REFERENCES product_units(id),
    delivered_quantity NUMERIC(24,6) NOT NULL CHECK (delivered_quantity>0),
    unit_price NUMERIC(18,2) NOT NULL CHECK (unit_price>=0),
    subtotal NUMERIC(18,2) NOT NULL CHECK (subtotal>=0),
    discount_amount NUMERIC(18,2) NOT NULL CHECK (discount_amount>=0),
    line_total NUMERIC(18,2) NOT NULL CHECK (line_total>=0),
    CHECK (subtotal-discount_amount=line_total)
);

CREATE TABLE IF NOT EXISTS invoice_adjustments (
    id BIGSERIAL PRIMARY KEY,
    adjustment_number VARCHAR(40) NOT NULL UNIQUE,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id),
    amount NUMERIC(18,2) NOT NULL CHECK (amount<>0),
    reason VARCHAR(1000) NOT NULL CHECK (length(trim(reason))>0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS invoices_customer_period ON invoices(customer_id,period_from,period_to);
CREATE INDEX IF NOT EXISTS invoice_lines_invoice ON invoice_lines(invoice_id,id);
CREATE INDEX IF NOT EXISTS invoice_adjustments_invoice ON invoice_adjustments(invoice_id,created_at);

CREATE OR REPLACE FUNCTION sprint7_immutable_invoice() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Hóa đơn đã phát hành không được sửa hoặc xóa; hãy lập chứng từ điều chỉnh' USING ERRCODE='P0001';
END $$;

DROP TRIGGER IF EXISTS invoices_immutable ON invoices;
CREATE TRIGGER invoices_immutable BEFORE UPDATE OR DELETE ON invoices
FOR EACH ROW EXECUTE FUNCTION sprint7_immutable_invoice();
DROP TRIGGER IF EXISTS invoice_lines_immutable ON invoice_lines;
CREATE TRIGGER invoice_lines_immutable BEFORE UPDATE OR DELETE ON invoice_lines
FOR EACH ROW EXECUTE FUNCTION sprint7_immutable_invoice();
DROP TRIGGER IF EXISTS invoice_deliveries_immutable ON invoice_deliveries;
CREATE TRIGGER invoice_deliveries_immutable BEFORE UPDATE OR DELETE ON invoice_deliveries
FOR EACH ROW EXECUTE FUNCTION sprint7_immutable_invoice();
DROP TRIGGER IF EXISTS invoice_adjustments_immutable ON invoice_adjustments;
CREATE TRIGGER invoice_adjustments_immutable BEFORE UPDATE OR DELETE ON invoice_adjustments
FOR EACH ROW EXECUTE FUNCTION sprint7_immutable_invoice();

DROP TRIGGER IF EXISTS catalog_audit ON invoices;
CREATE TRIGGER catalog_audit AFTER INSERT ON invoices FOR EACH ROW EXECUTE FUNCTION sprint2_audit();
DROP TRIGGER IF EXISTS catalog_audit ON invoice_adjustments;
CREATE TRIGGER catalog_audit AFTER INSERT ON invoice_adjustments FOR EACH ROW EXECUTE FUNCTION sprint2_audit();

COMMIT;

