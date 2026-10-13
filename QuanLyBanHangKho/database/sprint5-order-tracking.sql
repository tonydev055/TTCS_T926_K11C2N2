BEGIN;
-- S5-01: số lượng thực giao do nghiệp vụ giao hàng ghi nhận; NULL nghĩa là chưa ghi nhận.
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS delivered_base_quantity NUMERIC(24,6);
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='order_lines_delivered_valid') THEN
  ALTER TABLE order_lines ADD CONSTRAINT order_lines_delivered_valid
   CHECK(delivered_base_quantity IS NULL OR (delivered_base_quantity>=0 AND delivered_base_quantity<=base_quantity));
 END IF;
END $$;
COMMIT;
