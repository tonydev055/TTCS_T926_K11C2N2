BEGIN;
-- S3-01: ghi nhật ký mọi thay đổi chính sách chiết khấu và bậc chiết khấu.
-- Cần chạy sau sprint2.sql (hàm sprint2_audit) và sprint3.sql (bảng chiết khấu).
DROP TRIGGER IF EXISTS catalog_audit ON discount_policies;
CREATE TRIGGER catalog_audit AFTER INSERT OR UPDATE OR DELETE ON discount_policies
 FOR EACH ROW EXECUTE FUNCTION sprint2_audit();

-- discount_tiers không có cột id: ghi theo mã chính sách để lọc nhật ký cùng chính sách.
CREATE OR REPLACE FUNCTION discount_tier_audit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE actor BIGINT; before_data JSONB; after_data JSONB;
BEGIN
 actor=NULLIF(current_setting('app.actor_id',true),'')::BIGINT;
 IF TG_OP<>'INSERT' THEN before_data=to_jsonb(OLD); END IF;
 IF TG_OP<>'DELETE' THEN after_data=to_jsonb(NEW); END IF;
 INSERT INTO audit_logs(actor_user_id,action,object_type,object_id,old_value,new_value)
 VALUES(actor,TG_OP,TG_TABLE_NAME,COALESCE(after_data->>'policy_id',before_data->>'policy_id'),
        before_data::TEXT,after_data::TEXT);
 RETURN NULL;
END $$;
DROP TRIGGER IF EXISTS catalog_audit ON discount_tiers;
CREATE TRIGGER catalog_audit AFTER INSERT OR UPDATE OR DELETE ON discount_tiers
 FOR EACH ROW EXECUTE FUNCTION discount_tier_audit();
COMMIT;
