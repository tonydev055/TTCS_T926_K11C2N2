BEGIN;
-- S6-03, sau các migration Sprint 5. Không tự chuyển trạng thái đơn cũ.
CREATE TABLE IF NOT EXISTS pick_lists (
 id BIGSERIAL PRIMARY KEY, code VARCHAR(80) NOT NULL UNIQUE,
 warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
 status VARCHAR(16) NOT NULL DEFAULT 'PICKING' CHECK(status IN ('PICKING','COMPLETED')),
 created_by BIGINT NOT NULL REFERENCES users(id), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 completed_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS pick_list_orders (
 pick_list_id BIGINT NOT NULL REFERENCES pick_lists(id),
 order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id), PRIMARY KEY(pick_list_id,order_id)
);
CREATE TABLE IF NOT EXISTS pick_list_lines (
 id BIGSERIAL PRIMARY KEY, pick_list_id BIGINT NOT NULL REFERENCES pick_lists(id),
 product_id BIGINT NOT NULL REFERENCES products(id), location_id BIGINT REFERENCES warehouse_locations(id),
 requested_quantity NUMERIC(24,6) NOT NULL CHECK(requested_quantity>0),
 actual_quantity NUMERIC(24,6), picked BOOLEAN NOT NULL DEFAULT FALSE,
 shortage_reason VARCHAR(1000) NOT NULL DEFAULT '',
 CHECK(actual_quantity IS NULL OR actual_quantity BETWEEN 0 AND requested_quantity),
 CHECK(NOT picked OR actual_quantity IS NOT NULL),
 CHECK(NOT picked OR actual_quantity>=requested_quantity OR length(btrim(shortage_reason))>0)
);
CREATE INDEX IF NOT EXISTS pick_list_lines_parent ON pick_list_lines(pick_list_id);
COMMIT;
