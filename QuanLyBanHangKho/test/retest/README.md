# Kiểm thử API Sprint 1–2

Chỉ chạy trên môi trường phát triển cục bộ có PostgreSQL, migration Sprint 1–2 và Tomcat đang chạy bản build mới.

1. Chạy `scripts/build-sprint2.ps1 -Test` từ thư mục dự án.
2. Cấu hình `PGPASSWORD` cho PostgreSQL trong phiên PowerShell của bạn.
3. Chạy `test/retest/run-retest.ps1` từ thư mục dự án. Có thể truyền `-Psql`, `-JavaHome`, `-Database`, `-DatabaseUser`, `-DatabaseHost`.

Runner tạo namespace `qa` riêng, seed 7 vai trò, sinh XLSX/ảnh, chạy 6 bài hồi quy và 166 kiểm tra API/Excel/ảnh. Khối `finally` xóa các bản ghi và nhật ký thuộc namespace đó, không xóa dữ liệu nghiệp vụ khác. Kết quả chi tiết ghi vào `reports/api-retest-results.json` và `reports/import-retest-results.json`. Không dùng tài khoản hoặc mật khẩu admin của người vận hành.

Các file `*-before-fixes.json` là bằng chứng lần chạy trước khi sửa; lỗi trong các file đó không phải kết quả hiện tại. Số kiểm tra là số assertion/kịch bản, không phải tỷ lệ bao phủ mã nguồn.
