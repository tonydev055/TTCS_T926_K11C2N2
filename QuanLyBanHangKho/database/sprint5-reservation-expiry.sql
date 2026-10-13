BEGIN;
-- S5-06: mặc định 24 giờ, quản lý đổi được 1–168 giờ. Đơn hết hạn quay về Chờ duyệt.
CREATE TABLE IF NOT EXISTS stock_reservation_settings(id INTEGER PRIMARY KEY CHECK(id=1),hold_hours INTEGER NOT NULL CHECK(hold_hours BETWEEN 1 AND 168));
INSERT INTO stock_reservation_settings VALUES(1,24) ON CONFLICT DO NOTHING;
ALTER TABLE stock_reservations ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP+INTERVAL '24 hours');
CREATE INDEX IF NOT EXISTS reservations_expiry ON stock_reservations(expires_at) WHERE status='ACTIVE';
CREATE OR REPLACE FUNCTION reservation_set_deadline() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 NEW.expires_at=CURRENT_TIMESTAMP+(SELECT hold_hours FROM stock_reservation_settings WHERE id=1)*INTERVAL '1 hour';
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS reservation_deadline ON stock_reservations;
CREATE TRIGGER reservation_deadline BEFORE INSERT ON stock_reservations FOR EACH ROW EXECUTE FUNCTION reservation_set_deadline();
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
  WHEN 'APPROVED'         THEN ARRAY['PICKING','CANCELLED','PENDING_APPROVAL']
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

COMMIT;