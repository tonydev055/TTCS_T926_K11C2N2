BEGIN;
-- S4-06: vòng đời và huỷ đơn hàng. Chạy sau sprint3-customer-lock.sql. An toàn khi chạy lại.
--
-- Vòng đời: DRAFT (Nháp) → PENDING_APPROVAL (Chờ duyệt) → APPROVED (Đã duyệt) → PICKING (Đang soạn hàng)
--           → SHIPPED (Đã xuất) → DELIVERED (Đã giao) → CLOSED (Đóng).
-- Nhánh phụ: PENDING_CONFIRM (đại lý tự đặt, chờ nhân viên xác nhận), REJECTED (Từ chối), CANCELLED (Huỷ).
-- Đơn đã xuất kho không huỷ được, phải đi đường trả hàng.

ALTER TABLE orders ADD COLUMN IF NOT EXISTS source VARCHAR(10) NOT NULL DEFAULT 'STAFF';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipped_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(500);

-- Trạng thái cũ: S3-09 dùng 'COMPLETED', dữ liệu mẫu dùng 'CONFIRMED'. Các đơn này chưa được ai duyệt
-- và chưa giữ chỗ tồn, nên đưa về Chờ duyệt để quản lý kinh doanh xem lại.
UPDATE orders SET status='PENDING_APPROVAL', submitted_at=COALESCE(submitted_at, updated_at, created_at)
 WHERE status NOT IN ('DRAFT','PENDING_CONFIRM','PENDING_APPROVAL','APPROVED','PICKING','SHIPPED',
                      'DELIVERED','CLOSED','REJECTED','CANCELLED');

DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='orders_status_valid') THEN
  ALTER TABLE orders ADD CONSTRAINT orders_status_valid CHECK (status IN
   ('DRAFT','PENDING_CONFIRM','PENDING_APPROVAL','APPROVED','PICKING','SHIPPED','DELIVERED','CLOSED','REJECTED','CANCELLED'));
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='orders_source_valid') THEN
  ALTER TABLE orders ADD CONSTRAINT orders_source_valid CHECK (source IN ('STAFF','PORTAL'));
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='orders_cancel_reason_required') THEN
  ALTER TABLE orders ADD CONSTRAINT orders_cancel_reason_required
   CHECK (status <> 'CANCELLED' OR length(trim(coalesce(cancel_reason,''))) > 0);
 END IF;
END $$;

-- Lịch sử trạng thái: chỉ được thêm, không sửa, không xoá.
CREATE TABLE IF NOT EXISTS order_status_history (
 id BIGSERIAL PRIMARY KEY,
 order_id BIGINT NOT NULL REFERENCES orders(id),
 from_status VARCHAR(40),
 to_status VARCHAR(40) NOT NULL,
 reason VARCHAR(1000) NOT NULL DEFAULT '',
 changed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
 changed_by_name VARCHAR(150) NOT NULL,
 changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS order_status_history_order ON order_status_history(order_id, changed_at, id);

CREATE OR REPLACE FUNCTION order_history_readonly() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Lịch sử đơn hàng chỉ được thêm mới, không được sửa hoặc xoá' USING ERRCODE='P0001';
END $$;
DROP TRIGGER IF EXISTS order_status_history_readonly ON order_status_history;
CREATE TRIGGER order_status_history_readonly BEFORE UPDATE OR DELETE ON order_status_history
 FOR EACH ROW EXECUTE FUNCTION order_history_readonly();

-- Kiểm tra bước chuyển trạng thái và ghi mốc thời gian. Áp dụng cho mọi đường ghi (API hay SQL trực tiếp).
CREATE OR REPLACE FUNCTION order_status_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE allowed TEXT[];
BEGIN
 IF TG_OP = 'INSERT' THEN
  IF NEW.status NOT IN ('DRAFT','PENDING_CONFIRM') THEN
   RAISE EXCEPTION 'Đơn mới chỉ được tạo ở trạng thái Nháp hoặc Chờ xác nhận' USING ERRCODE='P0001';
  END IF;
  IF NEW.status = 'PENDING_CONFIRM' THEN NEW.submitted_at = COALESCE(NEW.submitted_at, CURRENT_TIMESTAMP); END IF;
  RETURN NEW;
 END IF;
 IF NEW.status = OLD.status THEN RETURN NEW; END IF;
 allowed = CASE OLD.status
  WHEN 'DRAFT'            THEN ARRAY['PENDING_APPROVAL','APPROVED','CANCELLED']
  WHEN 'PENDING_CONFIRM'  THEN ARRAY['PENDING_APPROVAL','APPROVED','CANCELLED']
  WHEN 'PENDING_APPROVAL' THEN ARRAY['APPROVED','REJECTED','DRAFT','CANCELLED']
  WHEN 'APPROVED'         THEN ARRAY['PICKING','CANCELLED']
  WHEN 'PICKING'          THEN ARRAY['SHIPPED','CANCELLED']
  WHEN 'SHIPPED'          THEN ARRAY['DELIVERED']
  WHEN 'DELIVERED'        THEN ARRAY['CLOSED']
  ELSE ARRAY[]::TEXT[] END;
 IF NOT (NEW.status = ANY(allowed)) THEN
  IF OLD.status IN ('SHIPPED','DELIVERED','CLOSED') AND NEW.status = 'CANCELLED' THEN
   RAISE EXCEPTION 'Đơn % đã xuất kho, không huỷ được. Hãy lập phiếu trả hàng', OLD.code USING ERRCODE='P0001';
  END IF;
  RAISE EXCEPTION 'Không thể chuyển đơn % từ trạng thái % sang %', OLD.code, OLD.status, NEW.status USING ERRCODE='P0001';
 END IF;
 NEW.updated_at = CURRENT_TIMESTAMP;
 CASE NEW.status
  WHEN 'PENDING_APPROVAL' THEN NEW.submitted_at = COALESCE(NEW.submitted_at, CURRENT_TIMESTAMP);
  WHEN 'APPROVED'  THEN NEW.approved_at = CURRENT_TIMESTAMP; NEW.submitted_at = COALESCE(NEW.submitted_at, CURRENT_TIMESTAMP);
  WHEN 'SHIPPED'   THEN NEW.shipped_at = CURRENT_TIMESTAMP;
  WHEN 'DELIVERED' THEN NEW.delivered_at = CURRENT_TIMESTAMP;
  WHEN 'CLOSED'    THEN NEW.closed_at = CURRENT_TIMESTAMP;
  WHEN 'CANCELLED' THEN NEW.cancelled_at = CURRENT_TIMESTAMP;
  WHEN 'DRAFT'     THEN NEW.submitted_at = NULL;
  ELSE NULL;
 END CASE;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS orders_status_guard ON orders;
CREATE TRIGGER orders_status_guard BEFORE INSERT OR UPDATE OF status ON orders
 FOR EACH ROW EXECUTE FUNCTION order_status_guard();

-- Ghi lịch sử sau mỗi lần tạo đơn hoặc đổi trạng thái.
-- Người thực hiện lấy từ app.actor_id, lý do lấy từ app.status_reason (đặt bằng set_config trong cùng giao dịch).
CREATE OR REPLACE FUNCTION order_status_log() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE actor BIGINT; actor_name VARCHAR(150);
BEGIN
 IF TG_OP = 'UPDATE' AND NEW.status = OLD.status THEN RETURN NULL; END IF;
 actor = NULLIF(current_setting('app.actor_id', true), '')::BIGINT;
 SELECT full_name INTO actor_name FROM users WHERE id = actor;
 INSERT INTO order_status_history(order_id, from_status, to_status, reason, changed_by, changed_by_name)
 VALUES(NEW.id, CASE WHEN TG_OP = 'UPDATE' THEN OLD.status END, NEW.status,
        left(COALESCE(NULLIF(current_setting('app.status_reason', true), ''), ''), 1000),
        actor, COALESCE(actor_name, 'Hệ thống'));
 RETURN NULL;
END $$;
DROP TRIGGER IF EXISTS orders_status_log ON orders;
CREATE TRIGGER orders_status_log AFTER INSERT OR UPDATE OF status ON orders
 FOR EACH ROW EXECUTE FUNCTION order_status_log();

-- Đơn có từ trước chưa có lịch sử: ghi một mốc khởi đầu để màn hình vòng đời không trống.
INSERT INTO order_status_history(order_id, from_status, to_status, reason, changed_by, changed_by_name, changed_at)
SELECT o.id, NULL, o.status, 'Đơn tạo trước khi có theo dõi vòng đời', o.created_by,
       COALESCE(u.full_name, 'Hệ thống'), COALESCE(o.updated_at, o.created_at)
FROM orders o LEFT JOIN users u ON u.id = o.created_by
WHERE NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = o.id);

CREATE INDEX IF NOT EXISTS orders_agent_status ON orders(agent_id, status);
CREATE INDEX IF NOT EXISTS orders_status_created ON orders(status, created_at DESC);
COMMIT;
