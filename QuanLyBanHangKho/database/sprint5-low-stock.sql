-- S5-09: ngưỡng theo đơn vị cơ sở cho từng sản phẩm/kho; phụ thuộc S5-03.
CREATE TABLE IF NOT EXISTS stock_thresholds (
 warehouse_id bigint NOT NULL REFERENCES warehouses(id),
 product_id bigint NOT NULL REFERENCES products(id),
 minimum_quantity numeric(24,6) NOT NULL CHECK (minimum_quantity>=0 AND minimum_quantity=trunc(minimum_quantity)),
 updated_by bigint REFERENCES users(id), updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(warehouse_id,product_id)
);
