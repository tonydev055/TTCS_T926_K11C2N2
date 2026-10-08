# KhoFlow — Hướng dẫn chạy dự án

Gửi email thật qua Gmail SMTP: xem [SMTP_SETUP.md](SMTP_SETUP.md). Chạy thêm `database/smtp.sql` trước khi build/test. Mặc định môi trường phát triển vẫn dùng KhoMail; bật `MAIL_MODE=smtp` cùng thông tin Gmail để chuyển sang gửi thật.

## Thành phần đã hoàn thiện

- Đăng nhập, đăng ký và phân quyền theo vai trò; quản trị viên có thể chỉnh sửa ma trận quyền trực tiếp trên giao diện.
- Khóa tạm thời sau 5 lần đăng nhập sai và khóa/mở khóa bởi quản trị viên.
- Quên mật khẩu, hộp thư thử nghiệm KhoMail và giao diện đặt lại mật khẩu.
- Trang Người dùng lấy dữ liệu thật từ PostgreSQL, có tìm kiếm, lọc và phân trang.
- Các danh sách sản phẩm, bảng giá, đơn hàng, đại lý, hóa đơn, thanh toán, trả hàng, tồn kho và nhà cung cấp đọc API thực; không dùng dữ liệu mẫu dự phòng.
- Đã bỏ số liệu mẫu ở tổng quan, menu và chế độ đăng nhập xem thử. Mục chưa kết nối API hiển thị thông báo chưa có nguồn dữ liệu; lỗi quyền truy cập hoặc thiếu bảng không được coi là danh sách rỗng.
- Sprint 2 đã có sản phẩm, cây nhóm hàng, đơn vị quy đổi, nhà cung cấp, nhóm khách hàng, bảng giá có phiên bản, nhập Excel, hồ sơ cá nhân, avatar và nhật ký. Chi tiết quyền và hướng dẫn tại `SPRINT2.md`.
- Các luồng tạo đơn, nhập/xuất kho, công nợ và báo cáo của sprint sau chưa được triển khai; không có dữ liệu mẫu dự phòng.
- PostgreSQL JDBC Driver đã nằm trong `web/WEB-INF/lib` và file WAR.
- Database Sprint 1 và dữ liệu demo nằm tại `database/sprint1.sql`.

## Cấu hình phát triển hiện tại

- Java: JDK 17
- Tomcat: 10.1
- PostgreSQL: 18
- Database: `quanlybanhangkho`
- Cổng PostgreSQL: `5432`
- Tài khoản PostgreSQL: `postgres`
- Mật khẩu PostgreSQL: `123456`
- Web khi chạy context hiện tại: `http://localhost:8080/QuanLyBanHangKho/`
- KhoMail: `http://localhost:8080/QuanLyBanHangKho/mailbox.html`

## Kiểm tra giao diện dữ liệu thực

Chạy `node --test test/frontend/real-data.test.cjs` từ thư mục dự án. Kiểm tra trạng thái rỗng, lỗi nguồn dữ liệu/quyền/phiên, tìm kiếm, phân trang, escape HTML và phản hồi đến muộn khi đổi màn hình. Dữ liệu kiểm thử chỉ nằm trong bộ nhớ, không được ghi vào database.

## Khởi tạo database

Database mới: chạy `database/sprint1.sql`, `database/sprint2.sql`, `database/role-permissions.sql`, `database/sprint3.sql`, `database/sprint3-price-history.sql`, `database/sprint3-discount-audit.sql`, `database/sprint3-customers.sql`, `database/sprint3-delivery-addresses.sql`, `database/sprint3-credit-limit.sql`, rồi `database/sprint3-customer-assignment.sql` trong database `quanlybanhangkho` bằng pgAdmin hoặc `psql -v ON_ERROR_STOP=1`. Database đang sử dụng cũng cần chạy các migration chưa áp dụng một lần để bật chức năng tương ứng. `sprint3-discount-audit.sql` ghi nhật ký thay đổi chính sách chiết khấu. `sprint3-customers.sql` tạo hồ sơ đại lý; `sprint3-delivery-addresses.sql` thêm điểm giao hàng; `sprint3-credit-limit.sql` thêm hạn mức công nợ; `sprint3-customer-assignment.sql` thêm lịch sử phân công nhân viên phụ trách. `sprint3-price-history.sql` lưu lịch sử giá chỉ đọc; database chặn trực tiếp việc sửa hoặc xóa các bản ghi này. Không có dữ liệu sản phẩm/đơn hàng mẫu được thêm bởi Sprint 2.

## Triển khai

- Có thể cấu hình Tomcat trỏ trực tiếp tới `build/web`.
- Hoặc triển khai file `dist/QuanLyBanHangKho.war` làm ứng dụng ROOT.
- Có thể build và kiểm thử bằng `./scripts/build-sprint2.ps1 -Test`.

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
