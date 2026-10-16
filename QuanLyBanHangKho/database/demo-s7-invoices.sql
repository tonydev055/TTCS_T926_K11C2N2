-- Dữ liệu demo S7-01 để kiểm thử thủ công màn hình Hóa đơn bán hàng.
-- Tạo 3 phiếu giao chưa xuất hóa đơn: 2 phiếu cùng đại lý và 1 phiếu đại lý khác.
-- Có thể chạy lại an toàn; dữ liệu đã xuất hóa đơn không bị đặt lại.
BEGIN;
SET LOCAL session_replication_role = replica;

INSERT INTO customer_groups(code,name)
VALUES ('S7-DEMO','Nhóm đại lý demo S7')
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name;

INSERT INTO categories(code,name)
VALUES ('S7-DEMO','Nhóm hàng demo S7')
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name;

INSERT INTO products(sku,name,category_id,base_unit,packaging,cost_price,active)
SELECT 'S7-DT-A','Điện thoại A · Demo hóa đơn',c.id,'Cái','Hộp',9000000,TRUE
FROM categories c WHERE c.code='S7-DEMO'
ON CONFLICT (sku) DO UPDATE SET name=EXCLUDED.name,active=TRUE;

INSERT INTO products(sku,name,category_id,base_unit,packaging,cost_price,active)
SELECT 'S7-TAI-NGHE','Tai nghe Pro · Demo hóa đơn',c.id,'Cái','Hộp',500000,TRUE
FROM categories c WHERE c.code='S7-DEMO'
ON CONFLICT (sku) DO UPDATE SET name=EXCLUDED.name,active=TRUE;

INSERT INTO product_units(product_id,name,factor,active)
SELECT p.id,'Cái',1,TRUE FROM products p WHERE p.sku IN ('S7-DT-A','S7-TAI-NGHE')
AND NOT EXISTS(SELECT 1 FROM product_units u WHERE u.product_id=p.id AND lower(u.name)='cái' AND u.active);

INSERT INTO customers(code,name,tax_code,customer_group_id,region,phone,status,credit_limit,credit_days)
SELECT 'DL-S7-MINH-PHAT','CÔNG TY TNHH MINH PHÁT','0312345678',g.id,'TP.HCM','0901234567','ACTIVE',500000000,30
FROM customer_groups g WHERE g.code='S7-DEMO'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,tax_code=EXCLUDED.tax_code,status='ACTIVE';

INSERT INTO customers(code,name,tax_code,customer_group_id,region,phone,status,credit_limit,credit_days)
SELECT 'DL-S7-HOANG-GIA','ĐẠI LÝ HOÀNG GIA','0312345688',g.id,'Bình Dương','0907654321','ACTIVE',300000000,30
FROM customer_groups g WHERE g.code='S7-DEMO'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,tax_code=EXCLUDED.tax_code,status='ACTIVE';

INSERT INTO orders(code,status,agent_id,created_by,delivery_address,desired_delivery_date,note,subtotal,discount_total,payable,delivered_at,updated_at)
SELECT 'S7-DEMO-001','DELIVERED',c.id,u.id,'18 Nguyễn Văn Linh, Quận 7, TP.HCM',CURRENT_DATE-2,'Phiếu giao demo số 1',129000000,5000000,124000000,CURRENT_TIMESTAMP-INTERVAL '2 days',CURRENT_TIMESTAMP
FROM customers c CROSS JOIN users u WHERE c.code='DL-S7-MINH-PHAT' AND u.email='accountant@khoflow.local'
ON CONFLICT (code) DO NOTHING;

INSERT INTO orders(code,status,agent_id,created_by,delivery_address,desired_delivery_date,note,subtotal,discount_total,payable,delivered_at,updated_at)
SELECT 'S7-DEMO-002','DELIVERED',c.id,u.id,'18 Nguyễn Văn Linh, Quận 7, TP.HCM',CURRENT_DATE-1,'Phiếu giao demo số 2',75000000,3000000,72000000,CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP
FROM customers c CROSS JOIN users u WHERE c.code='DL-S7-MINH-PHAT' AND u.email='accountant@khoflow.local'
ON CONFLICT (code) DO NOTHING;

INSERT INTO orders(code,status,agent_id,created_by,delivery_address,desired_delivery_date,note,subtotal,discount_total,payable,delivered_at,updated_at)
SELECT 'S7-DEMO-003','DELIVERED',c.id,u.id,'25 Đại lộ Bình Dương, Thủ Dầu Một',CURRENT_DATE,'Phiếu của đại lý khác',37500000,0,37500000,CURRENT_TIMESTAMP-INTERVAL '2 hours',CURRENT_TIMESTAMP
FROM customers c CROSS JOIN users u WHERE c.code='DL-S7-HOANG-GIA' AND u.email='accountant@khoflow.local'
ON CONFLICT (code) DO NOTHING;

-- Đơn 001: đặt 10 điện thoại nhưng thực giao 8; đặt 5 tai nghe nhưng thực giao 4.
INSERT INTO order_lines(order_id,product_id,unit_id,quantity,conversion_factor,base_price,discount_amount,line_total,list_price,floor_price,price_overridden,delivered_base_quantity)
SELECT o.id,p.id,u.id,10,1,12500000,5000000,120000000,12500000,12000000,FALSE,8
FROM orders o JOIN products p ON p.sku='S7-DT-A' JOIN product_units u ON u.product_id=p.id AND lower(u.name)='cái' AND u.active
WHERE o.code='S7-DEMO-001' AND NOT EXISTS(SELECT 1 FROM order_lines l WHERE l.order_id=o.id AND l.product_id=p.id);

INSERT INTO order_lines(order_id,product_id,unit_id,quantity,conversion_factor,base_price,discount_amount,line_total,list_price,floor_price,price_overridden,delivered_base_quantity)
SELECT o.id,p.id,u.id,5,1,800000,0,4000000,800000,750000,FALSE,4
FROM orders o JOIN products p ON p.sku='S7-TAI-NGHE' JOIN product_units u ON u.product_id=p.id AND lower(u.name)='cái' AND u.active
WHERE o.code='S7-DEMO-001' AND NOT EXISTS(SELECT 1 FROM order_lines l WHERE l.order_id=o.id AND l.product_id=p.id);

-- Đơn 002 giao đủ 6 điện thoại.
INSERT INTO order_lines(order_id,product_id,unit_id,quantity,conversion_factor,base_price,discount_amount,line_total,list_price,floor_price,price_overridden,delivered_base_quantity)
SELECT o.id,p.id,u.id,6,1,12500000,3000000,72000000,12500000,12000000,FALSE,6
FROM orders o JOIN products p ON p.sku='S7-DT-A' JOIN product_units u ON u.product_id=p.id AND lower(u.name)='cái' AND u.active
WHERE o.code='S7-DEMO-002' AND NOT EXISTS(SELECT 1 FROM order_lines l WHERE l.order_id=o.id AND l.product_id=p.id);

-- Đơn 003 của đại lý khác: đặt 3, thực giao 2.
INSERT INTO order_lines(order_id,product_id,unit_id,quantity,conversion_factor,base_price,discount_amount,line_total,list_price,floor_price,price_overridden,delivered_base_quantity)
SELECT o.id,p.id,u.id,3,1,12500000,0,37500000,12500000,12000000,FALSE,2
FROM orders o JOIN products p ON p.sku='S7-DT-A' JOIN product_units u ON u.product_id=p.id AND lower(u.name)='cái' AND u.active
WHERE o.code='S7-DEMO-003' AND NOT EXISTS(SELECT 1 FROM order_lines l WHERE l.order_id=o.id AND l.product_id=p.id);

INSERT INTO delivery_confirmations(order_id,outcome,recipient_name,delivered_at,proof_image_path,created_by)
SELECT o.id,'SUCCESS','Nguyễn Văn Minh',o.delivered_at,'s7-demo-proof.png',u.id
FROM orders o CROSS JOIN users u
WHERE o.code IN ('S7-DEMO-001','S7-DEMO-002','S7-DEMO-003') AND u.email='accountant@khoflow.local'
AND NOT EXISTS(SELECT 1 FROM delivery_confirmations d WHERE d.order_id=o.id);

SET LOCAL session_replication_role = origin;
COMMIT;

