BEGIN;

-- S3-02: immutable selling-price history per product.
CREATE TABLE IF NOT EXISTS price_history (
 id BIGSERIAL PRIMARY KEY,
 product_id BIGINT NOT NULL REFERENCES products(id),
 price_list_id BIGINT,
 price_list_name VARCHAR(150) NOT NULL,
 price_list_version INTEGER NOT NULL CHECK(price_list_version >= 1),
 customer_group_name VARCHAR(150) NOT NULL,
 old_price NUMERIC(18,2) CHECK(old_price IS NULL OR old_price >= 0),
 new_price NUMERIC(18,2) CHECK(new_price IS NULL OR new_price >= 0),
 changed_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
 changed_by_name VARCHAR(150) NOT NULL,
 changed_by_email VARCHAR(255),
 effective_at DATE NOT NULL,
 changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(old_price IS NOT NULL OR new_price IS NOT NULL),
 CHECK(old_price IS NULL OR new_price IS NULL OR old_price <> new_price)
);

CREATE INDEX IF NOT EXISTS price_history_product_time
 ON price_history(product_id, effective_at DESC, changed_at DESC, id DESC);

CREATE OR REPLACE FUNCTION prevent_price_history_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Lịch sử giá là dữ liệu chỉ đọc, không được sửa hoặc xóa';
END $$;

DROP TRIGGER IF EXISTS price_history_immutable ON price_history;
CREATE TRIGGER price_history_immutable
 BEFORE UPDATE OR DELETE ON price_history
 FOR EACH ROW EXECUTE FUNCTION prevent_price_history_mutation();

-- Preserve existing prices as the first visible point in each product's history.
INSERT INTO price_history(
 product_id,price_list_id,price_list_name,price_list_version,customer_group_name,
 old_price,new_price,changed_by_name,effective_at,changed_at
)
SELECT l.product_id,pl.id,pl.name,pl.version,g.name,NULL,l.price,'Dữ liệu khởi tạo',
       pl.valid_from,pl.created_at AT TIME ZONE current_setting('TIMEZONE')
FROM price_list_lines l
JOIN price_lists pl ON pl.id=l.price_list_id
JOIN customer_groups g ON g.id=pl.customer_group_id
WHERE NOT EXISTS (
 SELECT 1 FROM price_history h
 WHERE h.product_id=l.product_id AND h.price_list_id=pl.id
);

COMMIT;
