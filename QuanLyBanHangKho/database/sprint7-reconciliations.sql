BEGIN;
CREATE OR REPLACE VIEW receivable_ledger_events AS
 SELECT customer_id,issued_at::date event_date,issued_at recorded_at,invoice_number reference,total_amount amount,'INVOICE'::text kind FROM invoices
 UNION ALL SELECT i.customer_id,a.created_at::date,a.created_at,a.adjustment_number,a.amount,'ADJUSTMENT' FROM invoice_adjustments a JOIN invoices i ON i.id=a.invoice_id
 UNION ALL SELECT customer_id,received_date,created_at,receipt_code,-amount,'PAYMENT' FROM receivable_payments
 UNION ALL SELECT p.customer_id,r.created_at::date,r.created_at,p.receipt_code||'/HT',p.amount,'REVERSAL' FROM payment_reversals r JOIN receivable_payments p ON p.id=r.payment_id;
CREATE TABLE IF NOT EXISTS debt_reconciliations(
 id BIGSERIAL PRIMARY KEY,customer_id BIGINT NOT NULL REFERENCES customers(id),
 period_from DATE NOT NULL,period_to DATE NOT NULL CHECK(period_to>=period_from),
 customer_code TEXT NOT NULL,customer_name TEXT NOT NULL,
 opening_balance NUMERIC(18,2) NOT NULL,debit NUMERIC(18,2) NOT NULL,credit NUMERIC(18,2) NOT NULL,closing_balance NUMERIC(18,2) NOT NULL,
 snapshot JSONB NOT NULL,created_by BIGINT NOT NULL REFERENCES users(id),created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(customer_id,period_from,period_to),CHECK(closing_balance=opening_balance+debit-credit)
);
CREATE TABLE IF NOT EXISTS debt_reconciliation_responses(
 reconciliation_id BIGINT PRIMARY KEY REFERENCES debt_reconciliations(id),
 status TEXT NOT NULL CHECK(status IN('CONFIRMED','DISPUTED')),
 reason VARCHAR(2000) NOT NULL DEFAULT '',dealer_balance NUMERIC(18,2),
 responded_by BIGINT NOT NULL REFERENCES users(id),created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(status<>'DISPUTED' OR length(trim(reason))>0)
);
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['debt_reconciliations','debt_reconciliation_responses'] LOOP
  EXECUTE format('DROP TRIGGER IF EXISTS recon_immutable ON %I',t);
  EXECUTE format('CREATE TRIGGER recon_immutable BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION sprint7_immutable_invoice()',t);
  EXECUTE format('DROP TRIGGER IF EXISTS catalog_audit ON %I',t);
  EXECUTE format('CREATE TRIGGER catalog_audit AFTER INSERT ON %I FOR EACH ROW EXECUTE FUNCTION sprint2_audit()',t);
 END LOOP;
END $$;
COMMIT;
