BEGIN;
ALTER TABLE stock_bins ADD COLUMN IF NOT EXISTS manufactured_date DATE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS expiry_alert_days INTEGER NOT NULL DEFAULT 30 CHECK(expiry_alert_days BETWEEN 0 AND 3650);
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='stock_bins_manufactured_before_expiry') THEN
 ALTER TABLE stock_bins ADD CONSTRAINT stock_bins_manufactured_before_expiry CHECK(manufactured_date IS NULL OR expiry_date IS NULL OR manufactured_date<=expiry_date);
 END IF;
END $$;
DROP TRIGGER IF EXISTS stock_bins_lot_metadata_audit ON stock_bins;
CREATE TRIGGER stock_bins_lot_metadata_audit AFTER UPDATE OF manufactured_date ON stock_bins FOR EACH ROW WHEN (OLD.manufactured_date IS DISTINCT FROM NEW.manufactured_date) EXECUTE FUNCTION sprint2_audit();
COMMIT;
