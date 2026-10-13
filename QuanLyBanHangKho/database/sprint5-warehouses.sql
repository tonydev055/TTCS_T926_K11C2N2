BEGIN;
-- S5-03, sau toàn bộ migration Sprint 4.
ALTER TABLE warehouses ADD COLUMN IF NOT EXISTS address VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE warehouses ADD COLUMN IF NOT EXISTS manager_id BIGINT REFERENCES users(id);
CREATE TABLE IF NOT EXISTS warehouse_locations (
 id BIGSERIAL PRIMARY KEY, warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
 code VARCHAR(40) NOT NULL, name VARCHAR(150) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
 UNIQUE(warehouse_id,code), UNIQUE(id,warehouse_id),
 CHECK(length(trim(code))>0 AND length(trim(name))>0)
);
DROP TRIGGER IF EXISTS warehouse_locations_audit ON warehouse_locations;
CREATE TRIGGER warehouse_locations_audit AFTER INSERT OR UPDATE OR DELETE ON warehouse_locations
 FOR EACH ROW EXECUTE FUNCTION sprint2_audit();
COMMIT;
