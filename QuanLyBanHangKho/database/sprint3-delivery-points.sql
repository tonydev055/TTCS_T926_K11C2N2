-- S3: nhiều điểm giao hàng cho một đại lý. Chạy sau sprint1.sql, sprint2.sql, sprint3.sql (và trước hoặc sau sprint3-orders.sql đều được).
BEGIN;

CREATE TABLE IF NOT EXISTS delivery_points (
 id BIGSERIAL PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES users(id),
 receiver_name VARCHAR(150) NOT NULL CHECK(length(trim(receiver_name)) > 0),
 phone VARCHAR(20) NOT NULL CHECK(length(trim(phone)) > 0),
 address TEXT NOT NULL CHECK(length(trim(address)) > 0),
 route_note TEXT NOT NULL DEFAULT '',
 is_default BOOLEAN NOT NULL DEFAULT FALSE,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Mỗi đại lý tối đa MỘT điểm mặc định: database tự chặn, không phụ thuộc vào code.
CREATE UNIQUE INDEX IF NOT EXISTS delivery_points_one_default
  ON delivery_points(customer_id) WHERE is_default AND active;
CREATE INDEX IF NOT EXISTS delivery_points_customer ON delivery_points(customer_id) WHERE active;

-- Đơn hàng trỏ tới điểm giao (địa chỉ được chép vào orders.delivery_address để đơn cũ không đổi khi điểm giao bị sửa).
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_point_id BIGINT REFERENCES delivery_points(id);

COMMIT;
