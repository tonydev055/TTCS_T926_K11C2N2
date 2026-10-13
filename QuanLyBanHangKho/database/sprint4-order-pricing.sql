BEGIN;
-- S4-01: giá và chiết khấu tự động, sửa giá thủ công và giá sàn. Chạy sau sprint4-order-lifecycle.sql.
-- Giá trên dòng đơn tính theo đơn vị cơ bản của sản phẩm (như order_lines.base_price).

-- Giá bảng giá và giá sàn tại thời điểm lưu, để đối chiếu khi duyệt dù bảng giá thay đổi về sau.
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS list_price NUMERIC(18,2);
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS floor_price NUMERIC(18,2);
-- TRUE khi nhân viên sửa giá thủ công thay cho giá bảng giá.
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS price_overridden BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE order_lines SET list_price = base_price WHERE list_price IS NULL;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='order_lines_prices_valid') THEN
  ALTER TABLE order_lines ADD CONSTRAINT order_lines_prices_valid
   CHECK (base_price >= 0 AND (list_price IS NULL OR list_price >= 0) AND (floor_price IS NULL OR floor_price >= 0));
 END IF;
END $$;

-- Lý do đơn cần duyệt, ghi lúc gửi đơn. Mỗi phần tử: {"code","message"}; mảng rỗng là không vi phạm.
-- code: BELOW_FLOOR (bán dưới giá sàn); các sprint sau bổ sung lý do khác.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS approval_flags JSONB NOT NULL DEFAULT '[]'::jsonb;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='orders_approval_flags_array') THEN
  ALTER TABLE orders ADD CONSTRAINT orders_approval_flags_array CHECK (jsonb_typeof(approval_flags) = 'array');
 END IF;
END $$;
COMMIT;
