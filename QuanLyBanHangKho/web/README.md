# Giao diện KhoFlow

Chạy ứng dụng trên Tomcat và mở `index.html`. Mỗi chức năng có HTML,
CSS và JavaScript trong cùng một file, được nạp vào trang chính.

## Tìm file cần sửa

| File | Chức năng | Hàm bắt đầu |
| --- | --- | --- |
| `danh-muc/index.html` | Sản phẩm, nhóm hàng, quy đổi, nhà cung cấp, nhóm khách hàng, bảng giá | `renderCatalog()` |
| `ho-so/index.html` | Hồ sơ và ảnh đại diện | `renderProfile()` |
| `nhap-excel/index.html` | Xem trước, xác nhận nhập, tải báo cáo | `renderExcelImport()` |
| `nhat-ky/index.html` | Nhật ký thao tác và bộ lọc | `renderAuditLog()` |
| `nguoi-dung/index.html` | Tạo, khóa, xóa và tìm tài khoản | `renderUsersModule()` |
| `tong-quan/index.html` | Truy cập nhanh | `renderRealHome()` |
| `xac-thuc/index.html` | Đăng nhập, đăng ký, quên và đổi mật khẩu | Các sự kiện form và `renderChangePassword()` |

`mailbox.html` là trang hộp thư phát triển độc lập.

## Phần dùng chung

- `assets/shared/api.js`: gọi backend bằng `apiRequest()`, tạo yêu cầu JSON
  bằng `jsonRequestOptions()`, kiểm tra quyền bằng `hasPermission()`.
- `assets/shared/forms.js`: `openFormDialog()` mở hộp thoại,
  `renderInputField()` tạo ô nhập.
- `assets/shared/routes.js`: `getAvailableMenu()` lấy menu theo vai trò,
  `renderFeatureView()` chọn chức năng cần hiển thị.
- `assets/shared/navigation.js`: mở trang, cập nhật menu và thanh tiêu đề.
- `assets/shared/state.js`: tài khoản hiện tại, vai trò và trạng thái trang.
- `assets/shared/data-table.js`: bảng dữ liệu dùng chung.
- `assets/shared/ui.js`: thông báo và xử lý văn bản trước khi đưa vào HTML.
- `assets/shared/styles.css`: CSS dùng chung; CSS riêng nằm trong file chức năng.
- `assets/shared/feature-loader.js`: tải các file khai báo bằng `data-feature`
  trong trang chính, chạy script theo thứ tự rồi khôi phục phiên đăng nhập.

## Khi chỉnh sửa

1. Tìm hàm hiển thị trang, rồi đến sự kiện của nút hoặc form cần sửa.
2. Đặt tên hàm theo việc nó làm, ví dụ `saveProfile()` hoặc `uploadProfileAvatar()`.
3. Viết từng bước xử lý trên dòng riêng; tách hàm khi một sự kiện làm quá nhiều việc.
4. Dữ liệu người dùng đưa vào HTML phải qua `escapeHtml()`.
5. Giữ kiểm tra `viewRevision` và `isConnected` khi nhận phản hồi bất đồng bộ,
   để phản hồi cũ không ghi đè trang vừa mở.
6. Khi đổi tên hàm dùng chung, cập nhật cả nơi gọi và file kiểm thử.

Chạy kiểm thử từ thư mục gốc repository:

```powershell
node --test QuanLyBanHangKho/test/frontend/real-data.test.cjs
```
