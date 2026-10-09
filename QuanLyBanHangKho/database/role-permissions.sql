BEGIN;

CREATE TABLE IF NOT EXISTS role_permissions (
  role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  permission_code VARCHAR(80) NOT NULL,
  PRIMARY KEY(role_id, permission_code),
  CHECK(length(trim(permission_code)) > 0)
);

WITH defaults(role_code, permission_code) AS (VALUES
  ('ADMIN','admin.users'),('ADMIN','admin.roles'),('ADMIN','admin.audit'),
  ('ADMIN','admin.settings'),('ADMIN','catalog.read'),('ADMIN','products.read'),
  ('ADMIN','products.write'),('ADMIN','units.write'),('ADMIN','suppliers.read'),
  ('ADMIN','suppliers.write'),('ADMIN','prices.write'),('ADMIN','inventory.read'),
  ('ADMIN','warehouses.manage'),
  ('SALES_MANAGER','products.read'),('SALES_MANAGER','products.write'),
  ('SALES_MANAGER','products.cost'),('SALES_MANAGER','prices.write'),
  ('SALES_MANAGER','customers.read'),('SALES_MANAGER','orders.read'),
  ('SALES_MANAGER','orders.approve'),('SALES_MANAGER','reports.sales'),
  ('SALES_REP','products.read'),('SALES_REP','customers.assigned'),
  ('SALES_REP','orders.own'),('SALES_REP','payments.route'),('SALES_REP','targets.own'),
  ('WH_MANAGER','products.read'),('WH_MANAGER','units.write'),
  ('WH_MANAGER','inventory.read'),('WH_MANAGER','inventory.write'),
  ('WH_MANAGER','warehouses.manage'),('WH_MANAGER','suppliers.read'),
  ('WH_MANAGER','suppliers.write'),('WH_MANAGER','reports.inventory'),
  ('WAREHOUSE','products.read'),('WAREHOUSE','units.write'),
  ('WAREHOUSE','suppliers.read'),('WAREHOUSE','suppliers.write'),
  ('WAREHOUSE','inventory.read'),('WAREHOUSE','inventory.write'),
  ('WAREHOUSE','shipments.write'),
  ('ACCOUNTANT','products.read'),('ACCOUNTANT','customers.read'),
  ('ACCOUNTANT','orders.read'),('ACCOUNTANT','invoices.write'),
  ('ACCOUNTANT','payments.write'),('ACCOUNTANT','receivables.read'),
  ('CUSTOMER','products.read'),('CUSTOMER','orders.self'),
  ('CUSTOMER','shipments.self'),('CUSTOMER','invoices.self'),('CUSTOMER','returns.self')
)
INSERT INTO role_permissions(role_id, permission_code)
SELECT r.id, d.permission_code
FROM defaults d JOIN roles r ON r.code=d.role_code
WHERE NOT EXISTS (SELECT 1 FROM role_permissions)
ON CONFLICT DO NOTHING;

COMMIT;
