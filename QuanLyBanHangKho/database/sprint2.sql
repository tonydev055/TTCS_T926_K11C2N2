BEGIN;
CREATE TABLE IF NOT EXISTS categories (
 id BIGSERIAL PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE, name VARCHAR(150) NOT NULL,
 parent_id BIGINT REFERENCES categories(id), CHECK(parent_id IS NULL OR parent_id<>id)
);
CREATE TABLE IF NOT EXISTS products (
 id BIGSERIAL PRIMARY KEY, sku VARCHAR(80) NOT NULL UNIQUE, name VARCHAR(200) NOT NULL,
 category_id BIGINT NOT NULL REFERENCES categories(id), base_unit VARCHAR(40) NOT NULL,
 packaging VARCHAR(255) NOT NULL DEFAULT '', cost_price NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(cost_price>=0),
 active BOOLEAN NOT NULL DEFAULT TRUE, image BYTEA, thumbnail BYTEA,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS product_units (
 id BIGSERIAL PRIMARY KEY, product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
 name VARCHAR(40) NOT NULL, factor NUMERIC(18,6) NOT NULL CHECK(factor>0),
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS product_units_current ON product_units(product_id,lower(name)) WHERE active;
CREATE TABLE IF NOT EXISTS suppliers (
 id BIGSERIAL PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE, name VARCHAR(200) NOT NULL,
 tax_code VARCHAR(40) NOT NULL, contact_name VARCHAR(150) NOT NULL,
 phone VARCHAR(20) NOT NULL DEFAULT '', payment_terms TEXT NOT NULL DEFAULT '', active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS customer_groups (
 id BIGSERIAL PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE, name VARCHAR(150) NOT NULL
);
ALTER TABLE users ADD COLUMN IF NOT EXISTS customer_group_id BIGINT REFERENCES customer_groups(id);
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar BYTEA;
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_thumbnail BYTEA;
CREATE TABLE IF NOT EXISTS price_lists (
 id BIGSERIAL PRIMARY KEY, name VARCHAR(150) NOT NULL, customer_group_id BIGINT NOT NULL REFERENCES customer_groups(id),
 valid_from DATE NOT NULL, valid_to DATE NOT NULL, version INTEGER NOT NULL DEFAULT 1,
 previous_id BIGINT REFERENCES price_lists(id), active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, CHECK(valid_to>=valid_from)
);
CREATE TABLE IF NOT EXISTS price_list_lines (
 id BIGSERIAL PRIMARY KEY, price_list_id BIGINT NOT NULL REFERENCES price_lists(id) ON DELETE CASCADE,
 product_id BIGINT NOT NULL REFERENCES products(id), price NUMERIC(18,2) NOT NULL CHECK(price>=0),
 floor_price NUMERIC(18,2) NOT NULL CHECK(floor_price>=0 AND floor_price<=price), UNIQUE(price_list_id,product_id)
);
-- Foundation for subsequent sprints: historical lines reference immutable conversion versions
-- and store their factor/quantity at posting time. No business records are seeded.
CREATE TABLE IF NOT EXISTS orders (
 id BIGSERIAL PRIMARY KEY, code VARCHAR(80) NOT NULL UNIQUE,
 customer_id BIGINT REFERENCES users(id), price_list_id BIGINT REFERENCES price_lists(id),
 status VARCHAR(40) NOT NULL DEFAULT 'DRAFT', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS order_lines (
 id BIGSERIAL PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES orders(id), product_id BIGINT NOT NULL REFERENCES products(id),
 unit_id BIGINT NOT NULL REFERENCES product_units(id), quantity NUMERIC(18,6) NOT NULL CHECK(quantity>0),
 conversion_factor NUMERIC(18,6) NOT NULL CHECK(conversion_factor>0),
 base_quantity NUMERIC(24,6) GENERATED ALWAYS AS (quantity*conversion_factor) STORED
);
CREATE TABLE IF NOT EXISTS stock_receipts (
 id BIGSERIAL PRIMARY KEY, code VARCHAR(80) NOT NULL UNIQUE, supplier_id BIGINT NOT NULL REFERENCES suppliers(id),
 warehouse_id BIGINT NOT NULL REFERENCES warehouses(id), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS stock_receipt_lines (
 id BIGSERIAL PRIMARY KEY, receipt_id BIGINT NOT NULL REFERENCES stock_receipts(id), product_id BIGINT NOT NULL REFERENCES products(id),
 unit_id BIGINT NOT NULL REFERENCES product_units(id), quantity NUMERIC(18,6) NOT NULL CHECK(quantity>0),
 conversion_factor NUMERIC(18,6) NOT NULL CHECK(conversion_factor>0),
 base_quantity NUMERIC(24,6) GENERATED ALWAYS AS (quantity*conversion_factor) STORED
);
CREATE OR REPLACE FUNCTION sprint2_line_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE u product_units;
BEGIN
 IF TG_OP='INSERT' OR NEW.unit_id<>OLD.unit_id THEN
  SELECT * INTO u FROM product_units WHERE id=NEW.unit_id AND product_id=NEW.product_id AND active FOR SHARE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Đơn vị không hợp lệ hoặc đã ngừng sử dụng'; END IF;
  NEW.conversion_factor=u.factor;
 ELSIF NEW.conversion_factor<>OLD.conversion_factor OR NEW.product_id<>OLD.product_id THEN
  RAISE EXCEPTION 'Không được thay đổi quy đổi của giao dịch đã ghi';
 END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS order_unit_snapshot ON order_lines;
CREATE TRIGGER order_unit_snapshot BEFORE INSERT OR UPDATE ON order_lines FOR EACH ROW EXECUTE FUNCTION sprint2_line_snapshot();
DROP TRIGGER IF EXISTS receipt_unit_snapshot ON stock_receipt_lines;
CREATE TRIGGER receipt_unit_snapshot BEFORE INSERT OR UPDATE ON stock_receipt_lines FOR EACH ROW EXECUTE FUNCTION sprint2_line_snapshot();
CREATE OR REPLACE FUNCTION sprint2_audit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE actor BIGINT; before_data JSONB; after_data JSONB;
BEGIN
 actor=NULLIF(current_setting('app.actor_id',true),'')::BIGINT;
 IF TG_OP<>'INSERT' THEN before_data=to_jsonb(OLD)-'image'-'thumbnail'-'avatar'-'avatar_thumbnail'; END IF;
 IF TG_OP<>'DELETE' THEN after_data=to_jsonb(NEW)-'image'-'thumbnail'-'avatar'-'avatar_thumbnail'; END IF;
 INSERT INTO audit_logs(actor_user_id,action,object_type,object_id,old_value,new_value)
 VALUES(actor,TG_OP,TG_TABLE_NAME,COALESCE(after_data->>'id',before_data->>'id'),before_data::TEXT,after_data::TEXT);
 RETURN NULL;
END $$;
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['products','categories','product_units','suppliers','customer_groups','price_lists','price_list_lines','orders','order_lines','stock_receipts','stock_receipt_lines'] LOOP
  EXECUTE format('DROP TRIGGER IF EXISTS catalog_audit ON %I',t);
  EXECUTE format('CREATE TRIGGER catalog_audit AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION sprint2_audit()',t);
 END LOOP;
 -- Attach to existing financial/stock tables too, if installed by another sprint.
 FOREACH t IN ARRAY ARRAY['inventory','stock_transactions','customers','invoices','payments'] LOOP
  IF to_regclass('public.'||t) IS NOT NULL THEN
   EXECUTE format('DROP TRIGGER IF EXISTS catalog_audit ON %I',t);
   EXECUTE format('CREATE TRIGGER catalog_audit AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION sprint2_audit()',t);
  END IF;
 END LOOP;
END $$;
CREATE INDEX IF NOT EXISTS audit_logs_filter ON audit_logs(object_type,created_at DESC);
COMMIT;
