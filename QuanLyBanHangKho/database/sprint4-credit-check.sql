BEGIN;
-- S4-02: kiểm tra hạn mức công nợ khi lập đơn. Chạy sau sprint4-order-pricing.sql và sprint3-credit-limit.sql.
--
-- TẠM THỜI đến Sprint 7 (hoá đơn, thanh toán): công nợ của đại lý được ước tính từ đơn hàng.
--   • Công nợ hiện tại = tổng phải thu của các đơn đã duyệt mà chưa đóng (Đã duyệt → Đã giao).
--     Tính cả đơn chưa xuất hoá đơn để không tách nhỏ đơn mà lách được hạn mức.
--   • Khoản quá hạn = đơn đã giao quá số ngày nợ cho phép (credit_days) mà chưa đóng.
-- Sprint 7 chỉ cần thay định nghĩa view này bằng số dư từ hoá đơn/thanh toán; API và giao diện giữ nguyên.
CREATE OR REPLACE VIEW customer_credit_status AS
SELECT c.id AS customer_id,
       c.credit_limit,
       c.credit_days,
       COALESCE(o.outstanding, 0)::NUMERIC(18,2) AS outstanding,
       (c.credit_limit - COALESCE(o.outstanding, 0))::NUMERIC(18,2) AS available,
       COALESCE(o.overdue_amount, 0)::NUMERIC(18,2) AS overdue_amount,
       COALESCE(o.overdue_orders, 0)::INTEGER AS overdue_orders,
       COALESCE(o.max_overdue_days, 0)::INTEGER AS max_overdue_days
FROM customers c
LEFT JOIN LATERAL (
  SELECT sum(x.payable) AS outstanding,
         sum(x.payable) FILTER (WHERE x.overdue_days > 0) AS overdue_amount,
         count(*) FILTER (WHERE x.overdue_days > 0) AS overdue_orders,
         max(x.overdue_days) FILTER (WHERE x.overdue_days > 0) AS max_overdue_days
  FROM (
    SELECT od.payable,
           CASE WHEN od.status = 'DELIVERED' AND od.delivered_at IS NOT NULL
                THEN CURRENT_DATE - (od.delivered_at::DATE + c.credit_days) ELSE 0 END AS overdue_days
    FROM orders od
    WHERE od.agent_id = c.id AND od.status IN ('APPROVED','PICKING','SHIPPED','DELIVERED')
  ) x
) o ON TRUE;

-- Đại lý có khoản quá hạn thì chặn hoàn toàn việc tạo đơn mới và gửi đơn, ở mọi kênh (nhân viên hay cổng đại lý).
-- Đơn đã gửi trước đó vẫn được duyệt/xử lý tiếp; quản lý thấy cảnh báo khi duyệt.
CREATE OR REPLACE FUNCTION block_orders_for_overdue_customer() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE k RECORD;
BEGIN
 IF NEW.agent_id IS NULL THEN RETURN NEW; END IF;
 IF TG_OP = 'UPDATE' AND NOT (OLD.status IN ('DRAFT','PENDING_CONFIRM') AND NEW.status IN ('PENDING_APPROVAL','APPROVED')) THEN
  RETURN NEW;
 END IF;
 SELECT c.code, s.overdue_orders, s.overdue_amount, s.max_overdue_days, s.credit_days INTO k
 FROM customers c JOIN customer_credit_status s ON s.customer_id = c.id WHERE c.id = NEW.agent_id;
 IF k.overdue_orders > 0 THEN
  RAISE EXCEPTION 'Đại lý % có % đơn nợ quá hạn (quá % ngày so với % ngày được nợ, tổng % đ). Không thể tạo hoặc gửi đơn mới cho tới khi thu hồi công nợ',
   k.code, k.overdue_orders, k.max_overdue_days, k.credit_days, replace(to_char(k.overdue_amount, 'FM999,999,999,999,990'), ',', '.') USING ERRCODE='P0001';
 END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS orders_block_overdue_customer ON orders;
CREATE TRIGGER orders_block_overdue_customer BEFORE INSERT OR UPDATE OF status ON orders
 FOR EACH ROW EXECUTE FUNCTION block_orders_for_overdue_customer();

-- approval_flags có thêm mã OVER_CREDIT: tổng đơn cộng công nợ hiện tại vượt hạn mức.
CREATE INDEX IF NOT EXISTS orders_agent_open ON orders(agent_id) WHERE status IN ('APPROVED','PICKING','SHIPPED','DELIVERED');
COMMIT;
