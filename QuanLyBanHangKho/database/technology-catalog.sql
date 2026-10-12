BEGIN;

CREATE TABLE IF NOT EXISTS brands (
 id BIGSERIAL PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE,
 name VARCHAR(150) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS product_models (
 id BIGSERIAL PRIMARY KEY, code VARCHAR(80) NOT NULL UNIQUE,
 name VARCHAR(200) NOT NULL, category_id BIGINT NOT NULL REFERENCES categories(id),
 brand_id BIGINT NOT NULL REFERENCES brands(id), active BOOLEAN NOT NULL DEFAULT TRUE
);
ALTER TABLE products ADD COLUMN IF NOT EXISTS model_id BIGINT REFERENCES product_models(id);
ALTER TABLE products ADD COLUMN IF NOT EXISTS condition VARCHAR(20) NOT NULL DEFAULT 'NEW'
 CHECK (condition IN ('NEW','USED','DISPLAY'));
CREATE INDEX IF NOT EXISTS products_model_idx ON products(model_id);
CREATE INDEX IF NOT EXISTS categories_parent_idx ON categories(parent_id);
CREATE TABLE IF NOT EXISTS product_attributes (
 id BIGSERIAL PRIMARY KEY,
 product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
 name VARCHAR(60) NOT NULL, value VARCHAR(250) NOT NULL,
 UNIQUE(product_id,name), CHECK(length(trim(name))>0 AND length(trim(value))>0)
);
CREATE UNIQUE INDEX IF NOT EXISTS product_attributes_name_idx ON product_attributes(product_id,lower(name));

-- Add reference categories only; existing categories and SKU records are preserved.
INSERT INTO categories(code,name) VALUES
 ('TECH-PHONE','Điện thoại'), ('TECH-COMPUTER','Máy tính'),
 ('TECH-TABLET','Máy tính bảng'), ('TECH-WEARABLE','Thiết bị đeo'),
 ('TECH-AUDIO','Âm thanh'), ('TECH-ACCESSORY','Phụ kiện'),
 ('TECH-COMPONENT','Linh kiện máy tính'), ('TECH-NETWORK','Thiết bị mạng')
ON CONFLICT(code) DO NOTHING;

INSERT INTO categories(code,name,parent_id)
SELECT child.code,child.name,parent.id FROM (VALUES
 ('TECH-SMARTPHONE','Điện thoại thông minh','TECH-PHONE'),
 ('TECH-FEATUREPHONE','Điện thoại phổ thông','TECH-PHONE'),
 ('TECH-LAPTOP','Laptop','TECH-COMPUTER'),
 ('TECH-DESKTOP','Máy tính để bàn','TECH-COMPUTER'),
 ('TECH-AIO','Máy tính All-in-One','TECH-COMPUTER'),
 ('TECH-SMARTWATCH','Đồng hồ thông minh','TECH-WEARABLE'),
 ('TECH-SMARTBAND','Vòng đeo thông minh','TECH-WEARABLE'),
 ('TECH-HEADPHONE','Tai nghe','TECH-AUDIO'),
 ('TECH-SPEAKER','Loa','TECH-AUDIO'),
 ('TECH-MICROPHONE','Micro','TECH-AUDIO'),
 ('TECH-POWER','Sạc và nguồn','TECH-ACCESSORY'),
 ('TECH-CONNECTION','Cáp và chuyển đổi','TECH-ACCESSORY'),
 ('TECH-PROTECTION','Bảo vệ thiết bị','TECH-ACCESSORY'),
 ('TECH-INPUT','Chuột, bàn phím','TECH-ACCESSORY'),
 ('TECH-BAG-STAND','Túi, balo, giá đỡ','TECH-ACCESSORY'),
 ('TECH-CPU-MAINBOARD','CPU, bo mạch chủ','TECH-COMPONENT'),
 ('TECH-RAM','RAM','TECH-COMPONENT'),
 ('TECH-STORAGE','SSD, HDD','TECH-COMPONENT'),
 ('TECH-GPU','Card đồ họa','TECH-COMPONENT'),
 ('TECH-CASE-POWER','Nguồn, vỏ máy, tản nhiệt','TECH-COMPONENT'),
 ('TECH-ROUTER','Router, bộ phát Wi-Fi','TECH-NETWORK'),
 ('TECH-SWITCH','Switch, bộ mở rộng sóng','TECH-NETWORK')
) child(code,name,parent_code) JOIN categories parent ON parent.code=child.parent_code
ON CONFLICT(code) DO NOTHING;

INSERT INTO categories(code,name,parent_id)
SELECT child.code,child.name,parent.id FROM (VALUES
 ('TECH-CHARGER','Củ sạc','TECH-POWER'),
 ('TECH-POWERBANK','Pin sạc dự phòng','TECH-POWER'),
 ('TECH-CABLE','Cáp kết nối','TECH-CONNECTION'),
 ('TECH-HUB','Hub, đầu chuyển','TECH-CONNECTION'),
 ('TECH-CASE','Ốp lưng, bao da','TECH-PROTECTION'),
 ('TECH-SCREENPROTECTOR','Miếng dán màn hình','TECH-PROTECTION')
) child(code,name,parent_code) JOIN categories parent ON parent.code=child.parent_code
ON CONFLICT(code) DO NOTHING;

INSERT INTO brands(code,name) VALUES
 ('APPLE','Apple'),('SAMSUNG','Samsung'),('XIAOMI','Xiaomi'),('OPPO','OPPO'),
 ('VIVO','vivo'),('HONOR','HONOR'),('REALME','realme'),('NOKIA','Nokia'),
 ('ASUS','ASUS'),('ACER','Acer'),('DELL','Dell'),('HP','HP'),('LENOVO','Lenovo'),
 ('SONY','Sony'),('LOGITECH','Logitech'),('ANKER','Anker'),('UGREEN','UGREEN')
ON CONFLICT(code) DO NOTHING;

DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['brands','product_models','product_attributes'] LOOP
  EXECUTE format('DROP TRIGGER IF EXISTS catalog_audit ON %I',t);
  EXECUTE format('CREATE TRIGGER catalog_audit AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION sprint2_audit()',t);
 END LOOP;
END $$;
COMMIT;
