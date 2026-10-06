# Danh mục công nghệ

Cấu trúc: danh mục nhiều cấp → mẫu sản phẩm → SKU. Thương hiệu gắn vào mẫu; màu sắc, bộ nhớ, RAM và các cấu hình khác gắn vào từng SKU. Giá và lịch sử giao dịch tiếp tục tham chiếu SKU hiện có.

Trong **Sản phẩm & bảng giá**, tạo thương hiệu, tạo mẫu với thương hiệu và danh mục, rồi tạo từng biến thể tại tab Sản phẩm. Nút **Xem SKU** mở các biến thể của mẫu. Chọn danh mục cha sẽ lọc cả danh mục con; có thể kết hợp thương hiệu và một thuộc tính.

SKU cũ chưa gắn mẫu vẫn sử dụng được. Khi SKU đã gắn mẫu, danh mục lấy từ mẫu và không đổi mẫu của SKU đó, để giữ liên kết lịch sử. Thay đổi danh mục của mẫu cập nhật danh mục các SKU liên quan.

## Cài đặt

Sau khi có schema `database/sprint2.sql`, build một lần bằng `scripts/build-sprint2.ps1`, rồi chạy từ thư mục dự án (thay đường dẫn Java theo máy):

```powershell
& 'C:/Program Files/Java/jdk-17/bin/java.exe' --class-path 'build/web/WEB-INF/classes;web/WEB-INF/lib/*' scripts/ApplyTechnologyCatalog.java database/technology-catalog.sql
./scripts/build-sprint2.ps1 -Test
```

Migration có thể chạy lại: bổ sung 35 danh mục công nghệ và 17 thương hiệu; không tạo mẫu/SKU bán hàng giả, không xóa danh mục hay sản phẩm cũ.

## Kiểm thử giao diện độc lập

```powershell
node test/frontend/catalog-preview.cjs
```

Mở `http://localhost:8091/index.html`, đăng nhập `preview@example.test` với mật khẩu bất kỳ không trống. Dữ liệu minh họa và mọi thao tác lưu chỉ nằm trong bộ nhớ của server mô phỏng, không kết nối database. Dừng server bằng Ctrl+C.
