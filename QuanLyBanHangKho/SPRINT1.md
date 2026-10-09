# Sprint 1 — Tài khoản và phân quyền

## Khởi tạo PostgreSQL

Tạo database `quanlybanhangkho`, sau đó chạy `database/sprint1.sql`. Thêm PostgreSQL JDBC Driver 42.x vào `web/WEB-INF/lib` hoặc Libraries của NetBeans.

Các biến môi trường tùy chọn:

```text
DB_URL=jdbc:postgresql://localhost:5432/quanlybanhangkho
DB_USER=postgres
DB_PASSWORD=123456
APP_URL=http://localhost:8080/
```

Email kích hoạt và đặt lại mật khẩu hỗ trợ Gmail SMTP qua hàng đợi transaction; xem `SMTP_SETUP.md`. Chế độ `dev` lưu KhoMail. Không ghi mật khẩu hoặc liên kết reset vào Tomcat Log.

## Tài khoản demo

Mật khẩu chung: `Demo@123`

| Vai trò | Email |
| --- | --- |
| Quản trị hệ thống | admin@khoflow.local |
| Quản lý kinh doanh | sales.manager@khoflow.local |
| Nhân viên kinh doanh | sales@khoflow.local |
| Quản lý kho | warehouse.manager@khoflow.local |
| Nhân viên kho | warehouse@khoflow.local |
| Kế toán công nợ | accountant@khoflow.local |
| Đại lý | customer@khoflow.local |

Nếu PostgreSQL chưa được cấu hình, bảy tài khoản trên vẫn mở được giao diện demo cục bộ. Các thao tác lưu dữ liệu thật cần database.
