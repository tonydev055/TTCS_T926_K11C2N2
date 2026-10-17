BEGIN;
CREATE TABLE IF NOT EXISTS route_collections(
 id BIGSERIAL PRIMARY KEY,customer_id BIGINT NOT NULL REFERENCES customers(id),amount NUMERIC(18,2) NOT NULL CHECK(amount>0),
 collected_date DATE NOT NULL,collected_by BIGINT NOT NULL REFERENCES users(id),note VARCHAR(1000) NOT NULL DEFAULT '',
 proof_image BYTEA NOT NULL CHECK(octet_length(proof_image) BETWEEN 1 AND 2097152),request_key UUID NOT NULL UNIQUE,
 status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN('PENDING','RECEIVED')),payment_id BIGINT UNIQUE REFERENCES receivable_payments(id),
 received_by BIGINT REFERENCES users(id),received_at TIMESTAMP,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK((status='PENDING' AND payment_id IS NULL AND received_by IS NULL AND received_at IS NULL) OR(status='RECEIVED' AND payment_id IS NOT NULL AND received_by IS NOT NULL AND received_at IS NOT NULL))
);
CREATE OR REPLACE FUNCTION protect_route_collection() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Không được xóa khoản thu theo tuyến' USING ERRCODE='P0001'; END IF;
 IF OLD.status<>'PENDING' OR NEW.status<>'RECEIVED' OR (NEW.id,NEW.customer_id,NEW.amount,NEW.collected_date,NEW.collected_by,NEW.note,NEW.proof_image,NEW.request_key,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.customer_id,OLD.amount,OLD.collected_date,OLD.collected_by,OLD.note,OLD.proof_image,OLD.request_key,OLD.created_at) THEN RAISE EXCEPTION 'Chỉ kế toán xác nhận khoản thu chờ nộp; không sửa chứng từ gốc' USING ERRCODE='P0001'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS route_collection_protect ON route_collections;
CREATE TRIGGER route_collection_protect BEFORE UPDATE OR DELETE ON route_collections FOR EACH ROW EXECUTE FUNCTION protect_route_collection();
DROP TRIGGER IF EXISTS catalog_audit ON route_collections;
-- Không ghi ảnh biên lai dạng nhị phân vào audit JSON.
CREATE INDEX IF NOT EXISTS route_collections_collector ON route_collections(collected_by,collected_date,id);
COMMIT;
