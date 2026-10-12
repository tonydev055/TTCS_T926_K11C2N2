BEGIN;
-- S3-01: separate from legacy discounts; targets use existing catalog IDs.
CREATE TABLE IF NOT EXISTS discount_policies (
 id BIGSERIAL PRIMARY KEY,
 name VARCHAR(200) NOT NULL CHECK(length(trim(name)) > 0),
 scope VARCHAR(10) NOT NULL CHECK(scope IN ('SKU','GROUP')),
 product_id BIGINT REFERENCES products(id),
 category_id BIGINT REFERENCES categories(id),
 dtype VARCHAR(10) NOT NULL CHECK(dtype IN ('PERCENT','FIXED')),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK((scope='SKU' AND product_id IS NOT NULL AND category_id IS NULL)
    OR (scope='GROUP' AND category_id IS NOT NULL AND product_id IS NULL))
);
CREATE TABLE IF NOT EXISTS discount_tiers (
 policy_id BIGINT NOT NULL REFERENCES discount_policies(id) ON DELETE CASCADE,
 qty_from INTEGER NOT NULL CHECK(qty_from >= 1),
 value NUMERIC(18,2) NOT NULL CHECK(value > 0),
 PRIMARY KEY(policy_id, qty_from)
);
CREATE INDEX IF NOT EXISTS discount_policy_product ON discount_policies(product_id) WHERE active;
CREATE INDEX IF NOT EXISTS discount_policy_category ON discount_policies(category_id) WHERE active;
-- Percent limits and nonempty tiers are also validated atomically by the service.
COMMIT;
