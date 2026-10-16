-- S7-02. Chạy sau sprint7-sales-invoices.sql; không thêm dữ liệu nghiệp vụ mẫu.
BEGIN;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS due_date DATE;
DROP TRIGGER IF EXISTS invoices_immutable ON invoices;
UPDATE invoices i SET due_date=i.issued_at::date+c.credit_days FROM customers c WHERE c.id=i.customer_id AND i.due_date IS NULL;
ALTER TABLE invoices ALTER COLUMN due_date SET NOT NULL;
CREATE TRIGGER invoices_immutable BEFORE UPDATE OR DELETE ON invoices FOR EACH ROW EXECUTE FUNCTION sprint7_immutable_invoice();
CREATE OR REPLACE FUNCTION invoice_payment_terms() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 SELECT NEW.issued_at::date+c.credit_days INTO NEW.due_date FROM customers c WHERE c.id=NEW.customer_id;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS invoice_payment_terms ON invoices;
CREATE TRIGGER invoice_payment_terms BEFORE INSERT ON invoices FOR EACH ROW EXECUTE FUNCTION invoice_payment_terms();
CREATE TABLE IF NOT EXISTS receivable_payments(
 id BIGSERIAL PRIMARY KEY,
 receipt_code TEXT NOT NULL UNIQUE,
 customer_id BIGINT NOT NULL REFERENCES customers(id),
 amount NUMERIC(18,2) NOT NULL CHECK(amount>0),
 payment_method VARCHAR(20) NOT NULL CHECK(payment_method IN('CASH','BANK_TRANSFER','OTHER')),
 received_date DATE NOT NULL,
 received_by BIGINT NOT NULL REFERENCES users(id),
 note VARCHAR(1000) NOT NULL DEFAULT '',
 request_key UUID NOT NULL UNIQUE,
 target_invoice_id BIGINT REFERENCES invoices(id),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS payment_allocations(
 payment_id BIGINT NOT NULL REFERENCES receivable_payments(id),
 invoice_id BIGINT NOT NULL REFERENCES invoices(id),
 amount NUMERIC(18,2) NOT NULL CHECK(amount>0),
 PRIMARY KEY(payment_id,invoice_id)
);
CREATE TABLE IF NOT EXISTS payment_reversals(
 id BIGSERIAL PRIMARY KEY,
 payment_id BIGINT NOT NULL UNIQUE REFERENCES receivable_payments(id),
 reason VARCHAR(1000) NOT NULL CHECK(length(trim(reason))>0),
 reversed_by BIGINT NOT NULL REFERENCES users(id),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS receivable_payments_customer ON receivable_payments(customer_id,received_date,id);
CREATE INDEX IF NOT EXISTS payment_allocations_invoice ON payment_allocations(invoice_id);
CREATE OR REPLACE VIEW receivable_invoice_balances AS
 SELECT i.id,i.invoice_number,i.customer_id,i.issued_at,i.due_date,i.total_amount,
 COALESCE(a.amount,0)::NUMERIC(18,2) AS adjustment_amount,
 COALESCE(p.amount,0)::NUMERIC(18,2) AS paid_amount,
 (i.total_amount+COALESCE(a.amount,0)-COALESCE(p.amount,0))::NUMERIC(18,2) AS outstanding
 FROM invoices i
 LEFT JOIN LATERAL(SELECT sum(amount) amount FROM invoice_adjustments WHERE invoice_id=i.id) a ON true
 LEFT JOIN LATERAL(SELECT sum(x.amount) amount FROM payment_allocations x WHERE x.invoice_id=i.id
 AND NOT EXISTS(SELECT 1 FROM payment_reversals r WHERE r.payment_id=x.payment_id)) p ON true;
-- Điều chỉnh hóa đơn dùng cùng khóa đại lý với thu tiền, không được giảm dưới số đã thu.
CREATE OR REPLACE FUNCTION protect_invoice_adjustment_balance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE customer BIGINT; balance NUMERIC;
BEGIN
 SELECT customer_id INTO customer FROM invoices WHERE id=NEW.invoice_id;
 PERFORM 1 FROM customers WHERE id=customer FOR UPDATE;
 SELECT outstanding INTO balance FROM receivable_invoice_balances WHERE id=NEW.invoice_id;
 IF balance+NEW.amount<0 THEN RAISE EXCEPTION 'Điều chỉnh làm số dư hóa đơn âm; hoàn tác khoản thu trước khi giảm hóa đơn' USING ERRCODE='P0001'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS invoice_adjustment_balance ON invoice_adjustments;
CREATE TRIGGER invoice_adjustment_balance BEFORE INSERT ON invoice_adjustments FOR EACH ROW EXECUTE FUNCTION protect_invoice_adjustment_balance();
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['receivable_payments','payment_allocations','payment_reversals'] LOOP
  EXECUTE format('DROP TRIGGER IF EXISTS financial_immutable ON %I',t);
  EXECUTE format('CREATE TRIGGER financial_immutable BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION sprint7_immutable_invoice()',t);
  EXECUTE format('DROP TRIGGER IF EXISTS catalog_audit ON %I',t);
  EXECUTE format('CREATE TRIGGER catalog_audit AFTER INSERT ON %I FOR EACH ROW EXECUTE FUNCTION sprint2_audit()',t);
 END LOOP;
END $$;
COMMIT;
