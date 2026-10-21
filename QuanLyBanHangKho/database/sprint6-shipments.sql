BEGIN;
-- S6-05 sau sprint6-pick-lists.sql: một dòng đơn có thể xuất từ nhiều lô.
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS stock_movements_kind_document_id_line_id_warehouse_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS stock_movements_document_bin
 ON stock_movements(kind,document_id,line_id,warehouse_id,bin_id) NULLS NOT DISTINCT;
-- Đơn giao bù cho phần còn nợ hàng: mỗi đơn gốc chỉ có một đơn bù còn hiệu lực (không tính đơn đã huỷ).
ALTER TABLE orders ADD COLUMN IF NOT EXISTS backorder_of BIGINT REFERENCES orders(id);
CREATE UNIQUE INDEX IF NOT EXISTS orders_one_active_backorder ON orders(backorder_of) WHERE backorder_of IS NOT NULL AND status<>'CANCELLED';
COMMIT;
