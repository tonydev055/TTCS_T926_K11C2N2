BEGIN;
-- S6-08: xác nhận kết quả giao hàng và lưu đường dẫn chứng từ.
-- Có thể chạy lại an toàn, kể cả khi bảng đã được tạo trước đó.
CREATE TABLE IF NOT EXISTS delivery_confirmations (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILED')),
    recipient_name VARCHAR(150),
    delivered_at TIMESTAMP,
    proof_image_path TEXT,
    failure_reason TEXT,
    returned_to_warehouse BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT delivery_success_required_fields CHECK (
        outcome <> 'SUCCESS' OR (
            recipient_name IS NOT NULL AND LENGTH(TRIM(recipient_name)) > 0
            AND delivered_at IS NOT NULL
            AND proof_image_path IS NOT NULL AND LENGTH(TRIM(proof_image_path)) > 0
        )
    ),
    CONSTRAINT delivery_failure_required_reason CHECK (
        outcome <> 'FAILED' OR (failure_reason IS NOT NULL AND LENGTH(TRIM(failure_reason)) > 0)
    )
);

ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS outcome VARCHAR(20);
ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(150);
ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP;
ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS proof_image_path TEXT;
ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS failure_reason TEXT;
ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS returned_to_warehouse BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS created_by BIGINT REFERENCES users(id);
ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_delivery_confirmations_order
    ON delivery_confirmations(order_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS delivery_confirmations_one_per_order
    ON delivery_confirmations(order_id);
CREATE INDEX IF NOT EXISTS idx_delivery_confirmations_recent
    ON delivery_confirmations(created_at DESC);

DROP TRIGGER IF EXISTS delivery_confirmations_audit ON delivery_confirmations;
CREATE TRIGGER delivery_confirmations_audit
AFTER INSERT OR UPDATE ON delivery_confirmations
FOR EACH ROW EXECUTE FUNCTION sprint2_audit();

-- Quản lý kho cũng được xác nhận kết quả giao (cổng quyền /api/delivery-confirmations là shipments.write).
INSERT INTO role_permissions(role_id,permission_code)
SELECT id,'shipments.write' FROM roles WHERE code='WH_MANAGER'
ON CONFLICT DO NOTHING;

-- Giao thất bại và hàng đã hoàn kho thì đơn được huỷ (Đã xuất → Đã huỷ); giao lại bằng "Sao chép thành đơn mới".
-- Bản này thay bản trong sprint5-reservation-expiry.sql; nếu chạy lại file Sprint 5 thì chạy lại file này sau đó.
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
  WHEN 'SHIPPED'          THEN CASE WHEN EXISTS(SELECT 1 FROM delivery_confirmations dc WHERE dc.order_id=NEW.id AND dc.outcome='FAILED' AND dc.returned_to_warehouse)
                                THEN ARRAY['DELIVERED','CANCELLED'] ELSE ARRAY['DELIVERED'] END
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
