BEGIN;
CREATE TABLE IF NOT EXISTS sales_targets(
 id BIGSERIAL PRIMARY KEY,
 period_month DATE NOT NULL CHECK(extract(day FROM period_month)=1),
 staff_id BIGINT NOT NULL REFERENCES users(id),region VARCHAR(150) NOT NULL CHECK(length(btrim(region))>0),
 target_amount NUMERIC(18,2) NOT NULL CHECK(target_amount>0),note VARCHAR(500) NOT NULL DEFAULT '',
 version INTEGER NOT NULL DEFAULT 1 CHECK(version>0),created_by BIGINT NOT NULL REFERENCES users(id),updated_by BIGINT NOT NULL REFERENCES users(id),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(period_month,staff_id,region)
);
CREATE TABLE IF NOT EXISTS sales_target_changes(
 id BIGSERIAL PRIMARY KEY,target_id BIGINT NOT NULL REFERENCES sales_targets(id),actor_id BIGINT NOT NULL REFERENCES users(id),
 old_amount NUMERIC(18,2),new_amount NUMERIC(18,2) NOT NULL,old_note TEXT,new_note TEXT NOT NULL,
 reason VARCHAR(1000) NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE OR REPLACE FUNCTION sales_target_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE reason TEXT:=COALESCE(current_setting('app.target_reason',true),'');
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Chỉ tiêu không được xoá; chỉnh sửa và ghi lý do thay đổi' USING ERRCODE='P0001'; END IF;
 IF TG_OP='UPDATE' THEN
  IF NEW.period_month<>OLD.period_month OR NEW.staff_id<>OLD.staff_id OR NEW.region<>OLD.region THEN
   RAISE EXCEPTION 'Không được đổi kỳ, nhân viên hoặc khu vực của chỉ tiêu đã tạo' USING ERRCODE='P0001';
  END IF;
  IF OLD.period_month<date_trunc('month',CURRENT_DATE)::date AND length(btrim(reason))=0 THEN
   RAISE EXCEPTION 'Chỉnh sửa chỉ tiêu kỳ cũ bắt buộc có lý do' USING ERRCODE='P0001';
  END IF;
  NEW.version:=OLD.version+1;NEW.updated_at:=CURRENT_TIMESTAMP;
 END IF;
 INSERT INTO sales_target_changes(target_id,actor_id,old_amount,new_amount,old_note,new_note,reason)
 VALUES(NEW.id,NEW.updated_by,CASE WHEN TG_OP='UPDATE' THEN OLD.target_amount END,NEW.target_amount,
 CASE WHEN TG_OP='UPDATE' THEN OLD.note END,NEW.note,reason);
 RETURN NEW;
END $$;
-- AFTER INSERT đã có bản ghi cha cho lịch sử; BEFORE UPDATE chặn sửa kỳ cũ và tăng version.
DROP TRIGGER IF EXISTS sales_target_insert_history ON sales_targets;
CREATE TRIGGER sales_target_insert_history AFTER INSERT ON sales_targets FOR EACH ROW EXECUTE FUNCTION sales_target_history();
DROP TRIGGER IF EXISTS sales_target_update_history ON sales_targets;
CREATE TRIGGER sales_target_update_history BEFORE UPDATE OR DELETE ON sales_targets FOR EACH ROW EXECUTE FUNCTION sales_target_history();
CREATE INDEX IF NOT EXISTS sales_targets_staff_period ON sales_targets(staff_id,period_month);
COMMIT;
