\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE qa_objects(kind text,id text);
INSERT INTO qa_objects SELECT 'products',id::text FROM products WHERE sku LIKE upper(:'prefix')||'%';
INSERT INTO qa_objects SELECT 'product_units',id::text FROM product_units WHERE product_id::text IN (SELECT id FROM qa_objects WHERE kind='products');
INSERT INTO qa_objects SELECT 'categories',id::text FROM categories WHERE code LIKE upper(:'prefix')||'%';
INSERT INTO qa_objects SELECT 'suppliers',id::text FROM suppliers WHERE code LIKE upper(:'prefix')||'%';
INSERT INTO qa_objects SELECT 'customer_groups',id::text FROM customer_groups WHERE code LIKE upper(:'prefix')||'%';
INSERT INTO qa_objects SELECT 'price_lists',id::text FROM price_lists WHERE customer_group_id::text IN (SELECT id FROM qa_objects WHERE kind='customer_groups');
INSERT INTO qa_objects SELECT 'price_list_lines',id::text FROM price_list_lines WHERE price_list_id::text IN (SELECT id FROM qa_objects WHERE kind='price_lists');
INSERT INTO qa_objects SELECT 'users',id::text FROM users WHERE username LIKE :'prefix'||'%';
DELETE FROM discount_policies WHERE product_id::text IN (SELECT id FROM qa_objects WHERE kind='products') OR category_id::text IN (SELECT id FROM qa_objects WHERE kind='categories');
-- Lịch sử giá chỉ đọc với ứng dụng; chỉ tạm tắt trigger trong transaction này để dọn dữ liệu QA
-- (xóa dòng của sản phẩm QA và để FK đặt NULL người sửa là tài khoản QA).
ALTER TABLE price_history DISABLE TRIGGER price_history_immutable;
DELETE FROM price_history WHERE product_id::text IN (SELECT id FROM qa_objects WHERE kind='products');
DELETE FROM price_lists WHERE id::text IN (SELECT id FROM qa_objects WHERE kind='price_lists');
DELETE FROM products WHERE id::text IN (SELECT id FROM qa_objects WHERE kind='products');
DELETE FROM categories WHERE id::text IN (SELECT id FROM qa_objects WHERE kind='categories');
DELETE FROM suppliers WHERE id::text IN (SELECT id FROM qa_objects WHERE kind='suppliers');
DELETE FROM customer_groups WHERE id::text IN (SELECT id FROM qa_objects WHERE kind='customer_groups');
DELETE FROM audit_logs a WHERE actor_user_id::text IN (SELECT id FROM qa_objects WHERE kind='users') OR EXISTS(SELECT 1 FROM qa_objects o WHERE o.kind=a.object_type AND o.id=a.object_id);
DELETE FROM dev_mailbox_messages WHERE recipient LIKE :'prefix'||'%';
DELETE FROM users WHERE id::text IN (SELECT id FROM qa_objects WHERE kind='users');
ALTER TABLE price_history ENABLE TRIGGER price_history_immutable;
SELECT count(*) AS remaining_qa_users FROM users WHERE username LIKE :'prefix'||'%';
COMMIT;
