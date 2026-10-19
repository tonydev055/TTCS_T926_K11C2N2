BEGIN;
-- S8-02: chứng từ điều chỉnh giảm trừ công nợ sinh từ phiếu trả hàng đã nhận.
-- Chạy sau sprint7-payments.sql và sprint8-returns.sql. Có thể chạy lại an toàn.
-- Mỗi phiếu trả chỉ sinh tối đa một chứng từ giảm trừ, liên kết hoá đơn gốc; hoá đơn gốc không bị sửa.
ALTER TABLE invoice_adjustments ADD COLUMN IF NOT EXISTS return_id BIGINT REFERENCES sales_returns(id);
CREATE UNIQUE INDEX IF NOT EXISTS invoice_adjustments_return ON invoice_adjustments(return_id) WHERE return_id IS NOT NULL;

-- Chứng từ gắn phiếu trả phải đúng hoá đơn gốc, phiếu đã được kho nhận và giảm đúng giá trị trả theo giá trên hoá đơn.
CREATE OR REPLACE FUNCTION sprint8_return_credit_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r sales_returns%ROWTYPE;
BEGIN
 IF NEW.return_id IS NULL THEN RETURN NEW; END IF;
 SELECT * INTO r FROM sales_returns WHERE id=NEW.return_id FOR SHARE;
 IF r.status<>'RECEIVED' THEN RAISE EXCEPTION 'Chỉ lập giảm trừ cho phiếu trả đã được kho nhận hàng' USING ERRCODE='P0001'; END IF;
 IF r.invoice_id<>NEW.invoice_id THEN RAISE EXCEPTION 'Chứng từ giảm trừ phải gắn đúng hoá đơn gốc của phiếu trả' USING ERRCODE='P0001'; END IF;
 IF NEW.amount<>-r.total_amount THEN RAISE EXCEPTION 'Số tiền giảm trừ phải bằng giá trị hàng trả theo hoá đơn gốc' USING ERRCODE='P0001'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS invoice_adjustment_return ON invoice_adjustments;
CREATE TRIGGER invoice_adjustment_return BEFORE INSERT ON invoice_adjustments FOR EACH ROW EXECUTE FUNCTION sprint8_return_credit_guard();
COMMIT;
