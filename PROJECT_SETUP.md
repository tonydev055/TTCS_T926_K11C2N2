# KhoFlow — Hướng dẫn chạy dự án

## Thành phần đã hoàn thiện

- Đăng nhập, đăng ký và phân quyền theo vai trò.
- Khóa tạm thời sau 5 lần đăng nhập sai và khóa/mở khóa bởi quản trị viên.
- Quên mật khẩu, hộp thư thử nghiệm KhoMail và giao diện đặt lại mật khẩu.
- Trang Người dùng lấy dữ liệu thật từ PostgreSQL, có tìm kiếm, lọc và phân trang.
- PostgreSQL JDBC Driver đã nằm trong `web/WEB-INF/lib` và file WAR.
- Database Sprint 1 và dữ liệu demo nằm tại `database/sprint1.sql`.

## Cấu hình phát triển hiện tại

- Java: JDK 21
- Tomcat: 10.1
- PostgreSQL: 17
- Database: `quanlybanhangkho`
- Cổng PostgreSQL: `5432`
- Tài khoản PostgreSQL: `postgres`
- Mật khẩu PostgreSQL: `123456`
- Web: `http://localhost:8080/`
- KhoMail: `http://localhost:8080/mailbox.html`

## Khởi tạo database

Chạy file `database/sprint1.sql` trong database `quanlybanhangkho` bằng pgAdmin hoặc `psql`.

## Triển khai

- Có thể cấu hình Tomcat trỏ trực tiếp tới `build/web`.
- Hoặc triển khai file `dist/QuanLyBanHangKho.war` làm ứng dụng ROOT.

## Tài khoản demo

Mật khẩu chung: `Demo@123`

- `admin@khoflow.local`
- `sales.manager@khoflow.local`
- `sales@khoflow.local`
- `warehouse.manager@khoflow.local`
- `warehouse@khoflow.local`
- `accountant@khoflow.local`
- `customer@khoflow.local`

KhoMail chỉ dùng cho môi trường phát triển cục bộ, không phải dịch vụ email công cộng.
