-- S7-04. Chạy sau sprint7-payments.sql.
BEGIN;
CREATE TABLE IF NOT EXISTS credit_risk_policy(id INTEGER PRIMARY KEY CHECK(id=1),over_limit_mode TEXT NOT NULL CHECK(over_limit_mode IN('BLOCK','REVIEW')),overdue_mode TEXT NOT NULL CHECK(overdue_mode IN('BLOCK','REVIEW')));
INSERT INTO credit_risk_policy VALUES(1,'REVIEW','BLOCK') ON CONFLICT DO NOTHING;
CREATE OR REPLACE VIEW customer_credit_status AS
 SELECT c.id customer_id,c.credit_limit,c.credit_days,
 (COALESCE(b.outstanding,0)+COALESCE(o.committed,0))::NUMERIC(18,2) outstanding,
 (c.credit_limit-COALESCE(b.outstanding,0)-COALESCE(o.committed,0))::NUMERIC(18,2) available,
 COALESCE(b.overdue_amount,0)::NUMERIC(18,2) overdue_amount,
 COALESCE(b.overdue_orders,0)::INTEGER overdue_orders,
 COALESCE(b.max_overdue_days,0)::INTEGER max_overdue_days
 FROM customers c
 LEFT JOIN LATERAL(SELECT sum(outstanding) outstanding,sum(outstanding) FILTER(WHERE due_date<CURRENT_DATE AND outstanding>0) overdue_amount,count(*) FILTER(WHERE due_date<CURRENT_DATE AND outstanding>0) overdue_orders,max(CURRENT_DATE-due_date) FILTER(WHERE due_date<CURRENT_DATE AND outstanding>0) max_overdue_days FROM receivable_invoice_balances WHERE customer_id=c.id) b ON true
 LEFT JOIN LATERAL(SELECT sum(payable) committed FROM orders o WHERE o.agent_id=c.id AND o.status IN('APPROVED','PICKING','SHIPPED','DELIVERED') AND NOT EXISTS(SELECT 1 FROM invoice_deliveries d WHERE d.order_id=o.id)) o ON true;
CREATE TABLE IF NOT EXISTS customer_credit_alerts(customer_id BIGINT PRIMARY KEY REFERENCES customers(id),over_limit BOOLEAN NOT NULL,overdue BOOLEAN NOT NULL,outstanding NUMERIC(18,2) NOT NULL,overdue_amount NUMERIC(18,2) NOT NULL,evaluated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS credit_risk_daily_runs(run_date DATE PRIMARY KEY,completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE OR REPLACE FUNCTION evaluate_customer_credit(customer BIGINT) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO customer_credit_alerts(customer_id,over_limit,overdue,outstanding,overdue_amount)
 SELECT customer_id,outstanding>credit_limit,overdue_orders>0,outstanding,overdue_amount FROM customer_credit_status WHERE customer_id=customer
 ON CONFLICT(customer_id) DO UPDATE SET over_limit=EXCLUDED.over_limit,overdue=EXCLUDED.overdue,outstanding=EXCLUDED.outstanding,overdue_amount=EXCLUDED.overdue_amount,evaluated_at=CURRENT_TIMESTAMP;
END $$;
CREATE OR REPLACE FUNCTION refresh_credit_after_finance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE customer BIGINT;
BEGIN
 IF TG_TABLE_NAME='payment_allocations' THEN SELECT customer_id INTO customer FROM receivable_payments WHERE id=NEW.payment_id;
 ELSIF TG_TABLE_NAME='payment_reversals' THEN SELECT customer_id INTO customer FROM receivable_payments WHERE id=NEW.payment_id;
 ELSIF TG_TABLE_NAME IN('invoice_adjustments','invoice_deliveries') THEN SELECT customer_id INTO customer FROM invoices WHERE id=NEW.invoice_id;
 ELSIF TG_TABLE_NAME='invoices' THEN customer=NEW.customer_id;
 ELSIF TG_TABLE_NAME='orders' THEN customer=NEW.agent_id;
 ELSE customer=NEW.id; END IF;
 IF customer IS NOT NULL THEN PERFORM evaluate_customer_credit(customer); END IF; RETURN NEW;
END $$;
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['payment_allocations','payment_reversals','invoice_adjustments','invoice_deliveries','invoices','orders','customers'] LOOP
  EXECUTE format('DROP TRIGGER IF EXISTS refresh_credit ON %I',t);
  EXECUTE format('CREATE TRIGGER refresh_credit AFTER INSERT OR UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION refresh_credit_after_finance()',t);
 END LOOP;
END $$;
CREATE OR REPLACE FUNCTION block_orders_for_overdue_customer() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE credit RECORD; policy RECORD; before_amount NUMERIC=0; over BOOLEAN; late BOOLEAN;
BEGIN
 IF NEW.agent_id IS NULL THEN RETURN NEW; END IF;
 IF TG_OP='UPDATE' AND NOT(OLD.status IN('DRAFT','PENDING_CONFIRM') AND NEW.status IN('PENDING_APPROVAL','APPROVED')) THEN RETURN NEW; END IF;
 PERFORM 1 FROM customers WHERE id=NEW.agent_id FOR UPDATE;
 SELECT * INTO credit FROM customer_credit_status WHERE customer_id=NEW.agent_id;
 SELECT * INTO policy FROM credit_risk_policy WHERE id=1;
 over=credit.outstanding+NEW.payable>credit.credit_limit;late=credit.overdue_orders>0;
 IF (late AND policy.overdue_mode='BLOCK') OR (over AND policy.over_limit_mode='BLOCK') THEN
  RAISE EXCEPTION 'Đại lý vượt hạn mức hoặc có công nợ quá hạn; chính sách hiện tại chặn đơn mới' USING ERRCODE='P0001';
 END IF;
 IF late THEN NEW.approval_flags=COALESCE(NEW.approval_flags,'[]'::jsonb)||jsonb_build_array(jsonb_build_object('code','OVERDUE','message','Đại lý có công nợ quá hạn; cần duyệt')); END IF;
 IF over THEN NEW.approval_flags=COALESCE(NEW.approval_flags,'[]'::jsonb)||jsonb_build_array(jsonb_build_object('code','OVER_CREDIT','message','Đại lý vượt hạn mức; cần duyệt')); END IF;
 IF (late OR over) AND NEW.status='APPROVED' THEN RAISE EXCEPTION 'Đơn rủi ro phải qua Chờ duyệt' USING ERRCODE='P0001'; END IF;
 RETURN NEW;
END $$;
SELECT evaluate_customer_credit(id) FROM customers;
COMMIT;
