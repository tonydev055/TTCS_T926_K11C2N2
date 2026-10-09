BEGIN;

-- Du lieu mau cho moi truong phat trien KhoFlow.
-- Script co the chay lai ma khong tao ban ghi trung theo ma nghiep vu.

INSERT INTO categories(code, name)
VALUES
  ('DO-UONG', 'Đồ uống'),
  ('THUC-PHAM', 'Thực phẩm'),
  ('GIA-VI', 'Gia vị')
ON CONFLICT(code) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO categories(code, name, parent_id)
SELECT 'NUOC-GIAI-KHAT', 'Nước giải khát', id
FROM categories
WHERE code = 'DO-UONG'
ON CONFLICT(code) DO UPDATE
SET name = EXCLUDED.name, parent_id = EXCLUDED.parent_id;

INSERT INTO categories(code, name, parent_id)
SELECT 'BANH-KEO', 'Bánh kẹo', id
FROM categories
WHERE code = 'THUC-PHAM'
ON CONFLICT(code) DO UPDATE
SET name = EXCLUDED.name, parent_id = EXCLUDED.parent_id;

INSERT INTO products(sku, name, category_id, base_unit, packaging, cost_price, active)
SELECT v.sku, v.name, c.id, v.base_unit, v.packaging, v.cost_price, TRUE
FROM (VALUES
  ('SP-NK-330', 'Nước khoáng 330ml', 'NUOC-GIAI-KHAT', 'Chai', 'Thùng 24 chai', 3500::NUMERIC),
  ('SP-NT-450', 'Trà chanh 450ml', 'NUOC-GIAI-KHAT', 'Chai', 'Thùng 24 chai', 6500::NUMERIC),
  ('SP-BQ-200', 'Bánh quy bơ 200g', 'BANH-KEO', 'Gói', 'Thùng 20 gói', 18000::NUMERIC),
  ('SP-DG-1KG', 'Đường tinh luyện 1kg', 'GIA-VI', 'Túi', 'Bao 20 túi', 20500::NUMERIC),
  ('SP-NM-500', 'Nước mắm 500ml', 'GIA-VI', 'Chai', 'Thùng 12 chai', 28000::NUMERIC)
) AS v(sku, name, category_code, base_unit, packaging, cost_price)
JOIN categories c ON c.code = v.category_code
ON CONFLICT(sku) DO UPDATE SET
  name = EXCLUDED.name,
  category_id = EXCLUDED.category_id,
  base_unit = EXCLUDED.base_unit,
  packaging = EXCLUDED.packaging,
  cost_price = EXCLUDED.cost_price,
  active = TRUE,
  updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_units(product_id, name, factor, active)
SELECT p.id, v.unit_name, v.factor, TRUE
FROM (VALUES
  ('SP-NK-330', 'Chai', 1::NUMERIC), ('SP-NK-330', 'Thùng', 24::NUMERIC),
  ('SP-NT-450', 'Chai', 1::NUMERIC), ('SP-NT-450', 'Thùng', 24::NUMERIC),
  ('SP-BQ-200', 'Gói', 1::NUMERIC), ('SP-BQ-200', 'Thùng', 20::NUMERIC),
  ('SP-DG-1KG', 'Túi', 1::NUMERIC), ('SP-DG-1KG', 'Bao', 20::NUMERIC),
  ('SP-NM-500', 'Chai', 1::NUMERIC), ('SP-NM-500', 'Thùng', 12::NUMERIC)
) AS v(sku, unit_name, factor)
JOIN products p ON p.sku = v.sku
WHERE NOT EXISTS (
  SELECT 1 FROM product_units u
  WHERE u.product_id = p.id AND lower(u.name) = lower(v.unit_name) AND u.active
);

INSERT INTO suppliers(code, name, tax_code, contact_name, phone, payment_terms, active)
VALUES
  ('NCC-MINH-PHAT', 'Công ty TNHH Minh Phát', '0312345678', 'Nguyễn Minh', '0901234567', 'Thanh toán trong 15 ngày', TRUE),
  ('NCC-AN-KHANG', 'Công ty CP An Khang', '0109876543', 'Trần Lan', '0912345678', 'Thanh toán trong 30 ngày', TRUE),
  ('NCC-VIET-XANH', 'Công ty Thực phẩm Việt Xanh', '0401122334', 'Lê Hùng', '0934567890', 'Thanh toán khi nhận hàng', TRUE)
ON CONFLICT(code) DO UPDATE SET
  name = EXCLUDED.name,
  tax_code = EXCLUDED.tax_code,
  contact_name = EXCLUDED.contact_name,
  phone = EXCLUDED.phone,
  payment_terms = EXCLUDED.payment_terms,
  active = TRUE;

INSERT INTO customer_groups(code, name)
VALUES
  ('DAI-LY', 'Đại lý'),
  ('BAN-LE', 'Khách bán lẻ'),
  ('VIP', 'Khách hàng VIP')
ON CONFLICT(code) DO UPDATE SET name = EXCLUDED.name;

UPDATE users
SET customer_group_id = (SELECT id FROM customer_groups WHERE code = 'DAI-LY')
WHERE email = 'customer@khoflow.local';

INSERT INTO price_lists(name, customer_group_id, valid_from, valid_to, version, active)
SELECT 'Bảng giá đại lý 2026', g.id, DATE '2026-01-01', DATE '2026-12-31', 1, TRUE
FROM customer_groups g
WHERE g.code = 'DAI-LY'
  AND NOT EXISTS (SELECT 1 FROM price_lists WHERE name = 'Bảng giá đại lý 2026' AND version = 1);

INSERT INTO price_lists(name, customer_group_id, valid_from, valid_to, version, active)
SELECT 'Bảng giá bán lẻ 2026', g.id, DATE '2026-01-01', DATE '2026-12-31', 1, TRUE
FROM customer_groups g
WHERE g.code = 'BAN-LE'
  AND NOT EXISTS (SELECT 1 FROM price_lists WHERE name = 'Bảng giá bán lẻ 2026' AND version = 1);

INSERT INTO price_list_lines(price_list_id, product_id, price, floor_price)
SELECT pl.id, p.id, v.price, v.floor_price
FROM (VALUES
  ('SP-NK-330', 5000::NUMERIC, 4200::NUMERIC),
  ('SP-NT-450', 9000::NUMERIC, 7800::NUMERIC),
  ('SP-BQ-200', 26000::NUMERIC, 22000::NUMERIC),
  ('SP-DG-1KG', 28000::NUMERIC, 24500::NUMERIC),
  ('SP-NM-500', 39000::NUMERIC, 34000::NUMERIC)
) AS v(sku, price, floor_price)
JOIN products p ON p.sku = v.sku
JOIN price_lists pl ON pl.name = 'Bảng giá đại lý 2026' AND pl.version = 1
ON CONFLICT(price_list_id, product_id) DO UPDATE
SET price = EXCLUDED.price, floor_price = EXCLUDED.floor_price;

INSERT INTO price_list_lines(price_list_id, product_id, price, floor_price)
SELECT pl.id, p.id, v.price, v.floor_price
FROM (VALUES
  ('SP-NK-330', 6000::NUMERIC, 4500::NUMERIC),
  ('SP-NT-450', 11000::NUMERIC, 8200::NUMERIC),
  ('SP-BQ-200', 32000::NUMERIC, 24000::NUMERIC),
  ('SP-DG-1KG', 32000::NUMERIC, 26000::NUMERIC),
  ('SP-NM-500', 45000::NUMERIC, 36000::NUMERIC)
) AS v(sku, price, floor_price)
JOIN products p ON p.sku = v.sku
JOIN price_lists pl ON pl.name = 'Bảng giá bán lẻ 2026' AND pl.version = 1
ON CONFLICT(price_list_id, product_id) DO UPDATE
SET price = EXCLUDED.price, floor_price = EXCLUDED.floor_price;

INSERT INTO orders(code, customer_id, price_list_id, status, created_at)
SELECT 'DH-MAU-001', u.id, pl.id, 'CONFIRMED', CURRENT_TIMESTAMP - INTERVAL '2 days'
FROM users u
JOIN price_lists pl ON pl.name = 'Bảng giá đại lý 2026' AND pl.version = 1
WHERE u.email = 'customer@khoflow.local'
ON CONFLICT(code) DO UPDATE SET status = EXCLUDED.status;

INSERT INTO orders(code, customer_id, price_list_id, status, created_at)
SELECT 'DH-MAU-002', u.id, pl.id, 'DRAFT', CURRENT_TIMESTAMP - INTERVAL '3 hours'
FROM users u
JOIN price_lists pl ON pl.name = 'Bảng giá đại lý 2026' AND pl.version = 1
WHERE u.email = 'customer@khoflow.local'
ON CONFLICT(code) DO UPDATE SET status = EXCLUDED.status;

INSERT INTO order_lines(order_id, product_id, unit_id, quantity, conversion_factor)
SELECT o.id, p.id, pu.id, v.quantity, pu.factor
FROM (VALUES
  ('DH-MAU-001', 'SP-NK-330', 'Thùng', 5::NUMERIC),
  ('DH-MAU-001', 'SP-BQ-200', 'Thùng', 2::NUMERIC),
  ('DH-MAU-002', 'SP-NT-450', 'Thùng', 3::NUMERIC),
  ('DH-MAU-002', 'SP-NM-500', 'Thùng', 1::NUMERIC)
) AS v(order_code, sku, unit_name, quantity)
JOIN orders o ON o.code = v.order_code
JOIN products p ON p.sku = v.sku
JOIN product_units pu ON pu.product_id = p.id AND lower(pu.name) = lower(v.unit_name) AND pu.active
WHERE NOT EXISTS (
  SELECT 1 FROM order_lines ol WHERE ol.order_id = o.id AND ol.product_id = p.id AND ol.unit_id = pu.id
);

INSERT INTO stock_receipts(code, supplier_id, warehouse_id, created_at)
SELECT 'PN-MAU-001', s.id, w.id, CURRENT_TIMESTAMP - INTERVAL '5 days'
FROM suppliers s CROSS JOIN warehouses w
WHERE s.code = 'NCC-MINH-PHAT' AND w.code = 'HCM'
ON CONFLICT(code) DO NOTHING;

INSERT INTO stock_receipts(code, supplier_id, warehouse_id, created_at)
SELECT 'PN-MAU-002', s.id, w.id, CURRENT_TIMESTAMP - INTERVAL '1 day'
FROM suppliers s CROSS JOIN warehouses w
WHERE s.code = 'NCC-AN-KHANG' AND w.code = 'HN'
ON CONFLICT(code) DO NOTHING;

INSERT INTO stock_receipt_lines(receipt_id, product_id, unit_id, quantity, conversion_factor)
SELECT r.id, p.id, pu.id, v.quantity, pu.factor
FROM (VALUES
  ('PN-MAU-001', 'SP-NK-330', 'Thùng', 50::NUMERIC),
  ('PN-MAU-001', 'SP-NT-450', 'Thùng', 35::NUMERIC),
  ('PN-MAU-002', 'SP-BQ-200', 'Thùng', 20::NUMERIC),
  ('PN-MAU-002', 'SP-DG-1KG', 'Bao', 15::NUMERIC),
  ('PN-MAU-002', 'SP-NM-500', 'Thùng', 25::NUMERIC)
) AS v(receipt_code, sku, unit_name, quantity)
JOIN stock_receipts r ON r.code = v.receipt_code
JOIN products p ON p.sku = v.sku
JOIN product_units pu ON pu.product_id = p.id AND lower(pu.name) = lower(v.unit_name) AND pu.active
WHERE NOT EXISTS (
  SELECT 1 FROM stock_receipt_lines rl
  WHERE rl.receipt_id = r.id AND rl.product_id = p.id AND rl.unit_id = pu.id
);

COMMIT;
