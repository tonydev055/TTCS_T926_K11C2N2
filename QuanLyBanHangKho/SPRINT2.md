# Sprint 2 — Danh mục hàng hóa và bảng giá

Nguồn yêu cầu: [Product Backlog, Sprint 2](https://docs.google.com/spreadsheets/d/1a6863CuTDEojgYEkhs8QcdnHs-WNtL86/edit?gid=1069279071). 10 story, 44 point.

## Chức năng

| Story | Vị trí trên giao diện | Triển khai |
|---|---|---|
| S2-01 | Admin → Nhập người dùng Excel | Tải XLSX mẫu, xem trước, kiểm tra từng dòng, xác nhận nhập, báo cáo CSV; dòng lỗi bỏ qua; tài khoản mới nhận mật khẩu tạm trong KhoMail |
| S2-02 | Hồ sơ cá nhân | Sửa họ tên và số điện thoại Việt Nam; server từ chối trường vai trò/tài khoản/kho/địa bàn |
| S2-03 | Hồ sơ cá nhân → Cập nhật ảnh | Kiểm nội dung JPG/PNG và giới hạn 2 MB; cắt vuông 256 px, thumbnail 64 px; lưu PostgreSQL |
| S2-04 | Admin → Nhật ký hệ thống | Người thực hiện, thời gian, trước/sau; lọc người dùng, đối tượng, ngày; phân trang; ẩn giá vốn với người không có SALES_MANAGER |
| S2-05 | Sản phẩm & bảng giá → Sản phẩm | CRUD SKU, nhóm hàng, đơn vị cơ sở, đóng gói, giá vốn, ảnh và trạng thái; SKU duy nhất; FK chặn xóa khi đã sử dụng |
| S2-06 | Sản phẩm & bảng giá → Nhóm hàng | Cây nhóm nhiều cấp; chọn nhóm cha; chống vòng lặp; chuyển nhóm sản phẩm; chặn xóa nhóm còn con/sản phẩm |
| S2-07 | Sản phẩm & bảng giá → Quy đổi đơn vị | Đơn vị cơ sở hệ số 1; đơn vị quy đổi có phiên bản mới mỗi lần sửa; dòng đơn/phiếu nhập lưu hệ số và số lượng cơ sở tại thời điểm ghi |
| S2-08 | Sản phẩm → Nhập Excel | Tải mẫu theo quyền giá vốn; xem trước tạo/cập nhật SKU; báo lỗi từng dòng; xác nhận bằng token phiên có hạn 15 phút, dùng một lần |
| S2-09 | Nhà cung cấp | Mã, tên, mã số thuế, người liên hệ, điện thoại, điều khoản thanh toán, trạng thái; chặn xóa khi có phiếu nhập |
| S2-10 | Sản phẩm & bảng giá → Bảng giá | Nhóm khách hàng, ngày hiệu lực, nhiều dòng giá bán/giá sàn; nhiều nhóm có bảng giá song song; bảng đã có đơn không sửa/xóa; tạo phiên bản mới |

## Quyền và quy tắc

- Quản lý kinh doanh quản lý sản phẩm, nhóm hàng, nhóm khách hàng, bảng giá và nhập sản phẩm Excel.
- Admin có quyền quản lý các danh mục Sprint 2, **không** xem/sửa giá vốn nếu không đồng thời có vai trò SALES_MANAGER. Đây là ngoại lệ riêng của backlog.
- Nhân viên kho và Quản lý kho quản lý nhà cung cấp và đơn vị quy đổi.
- Các vai trò khác chỉ xem danh mục sản phẩm. Đại lý chỉ thấy bảng giá còn hiệu lực của `users.customer_group_id`; chưa được gán nhóm thì không thấy bảng giá. Luồng hồ sơ đại lý và gán nhóm thuộc Sprint 3.
- Kiểm quyền tại filter trước khi gọi servlet; kiểm giá vốn thêm trong service. Giao diện sử dụng tập quyền từ server và hợp các menu khi có nhiều vai trò.
- Giá trong bảng giá theo đơn vị cơ sở. Giá sàn không lớn hơn giá bán; không cho hai bảng đang hoạt động trùng thời gian cho cùng nhóm. Phiên bản thay thế đã dùng cần chọn kỳ hiệu lực tiếp theo, không sửa kỳ cũ.
- Đơn vị cơ sở không đổi sau khi tạo SKU. Có thể thêm đơn vị quy đổi. Trigger tính snapshot khi ghi dòng đơn/phiếu nhập; không dùng hệ số do client cung cấp.
- Nhật ký danh mục và các bảng nền đơn/nhập kho nằm cùng transaction với dữ liệu gốc. Khi thêm bảng tài chính/tồn kho ở sprint sau, chạy lại phần gắn audit trigger trong migration; các service mới phải đặt `app.actor_id`.

## Cài đặt và kiểm thử

1. Với database mới, chạy `database/sprint1.sql` trước; database hiện có chỉ chạy `database/sprint2.sql` bằng `psql -v ON_ERROR_STOP=1`. Sprint 2 không seed dữ liệu nghiệp vụ.
2. Từ PowerShell chạy `./scripts/build-sprint2.ps1 -Test`. Có thể truyền `-TomcatHome` và `-JavaHome` phù hợp máy.
3. Tomcat đang trỏ `build/web`; thay đổi `WEB-INF/web.xml` kích hoạt reload. Hoặc triển khai `dist/QuanLyBanHangKho.war`.
4. Mở `http://localhost:8080/QuanLyBanHangKho/` và đăng nhập lại để lấy tập quyền mới.

Kiểm thử service dùng transaction rollback, bao gồm SKU trùng, quyền giá vốn, cây nhóm, quy đổi lịch sử, giới hạn giá, bảng giá đã dùng, FK bảo vệ dữ liệu, audit, nhập từng dòng, XLSX Unicode/công thức, ảnh và JSON. Không để lại sản phẩm, đơn hay người dùng thử nghiệm.

## Giới hạn phạm vi

- Nhập Excel hỗ trợ `.xlsx` theo mẫu, trang đầu tiên, tối đa 5.000 dòng/8 MB. Không hỗ trợ `.xls`, công thức hoặc macro; không tự diễn giải mã SKU dạng số đã bị Excel làm mất số 0 đầu.
- Email hỗ trợ Gmail SMTP qua hàng đợi transaction; xem `SMTP_SETUP.md`. Khi `MAIL_MODE=dev`, email vẫn chỉ lưu KhoMail.
- Tài khoản mới do Admin tạo thủ công hoặc nhập từ Excel dùng mật khẩu tạm `Demo@123` (chữ D hoa), lưu dạng hash và phải đổi khi đăng nhập lần đầu. Tài khoản đã tồn tại không bị đổi mật khẩu.
- Đã có bảng nền `orders`, `order_lines`, `stock_receipts`, `stock_receipt_lines` để bảo vệ tham chiếu và kiểm thử quy đổi; giao diện tạo đơn, nhập/xuất kho và toàn bộ workflow của các sprint sau chưa triển khai.
- Chưa đo coverage bằng công cụ, chưa có CI/staging hoặc nghiệm thu PO; không coi kiểm thử cục bộ là hoàn tất toàn bộ Definition of Done trong backlog.
