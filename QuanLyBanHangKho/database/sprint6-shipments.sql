BEGIN;
-- S6-05 sau sprint6-pick-lists.sql: một dòng đơn có thể xuất từ nhiều lô.
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS stock_movements_kind_document_id_line_id_warehouse_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS stock_movements_document_bin
 ON stock_movements(kind,document_id,line_id,warehouse_id,bin_id) NULLS NOT DISTINCT;
COMMIT;
