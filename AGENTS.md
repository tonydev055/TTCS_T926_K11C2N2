# Hướng dẫn AI làm việc với KhoFlow

Áp dụng cho repository `TTCS_T926_K11C2N2`. Ưu tiên yêu cầu trực tiếp của người dùng khi khác các quy ước dưới đây.

## Đọc hướng dẫn theo công việc

- Sửa FE, form, bố cục, menu hoặc CSS: đọc [SKILL_frontend_design.md](SKILL_frontend_design.md).
- Sửa Java, API, phân quyền hoặc database: đọc [SKILL_backend.md](SKILL_backend.md).
- Công việc có cả FE và backend: đọc cả hai trước khi sửa.
- Đọc phần code liên quan và một chức năng tương tự để hiểu luồng hiện tại trước khi sửa. Không cần đọc toàn repository hoặc mọi skill không liên quan.
- Các hướng dẫn của dự án đang nằm ở gốc như liên kết trên; không giả định có thư mục `SKILLS` chỉ vì tài liệu tham khảo dùng cấu trúc đó.

## Cấu trúc dự án

- `QuanLyBanHangKho/src/java/`: backend Java, các package `controller`, `service`, `db`, `model`, `security`, `filter`, `util`.
- `QuanLyBanHangKho/web/index.html`: khung ứng dụng, đăng nhập, menu và liên kết nạp module.
- `QuanLyBanHangKho/web/assets/shared/`: CSS, API client, điều hướng và các tiện ích dùng chung.
- `QuanLyBanHangKho/web/<chuc-nang>/index.html`: FE từng chức năng, ví dụ `ho-so`, `nguoi-dung`, `chiet-khau`.
- `QuanLyBanHangKho/database/`: migration PostgreSQL.
- `QuanLyBanHangKho/scripts/build-sprint2.ps1`: script build hiện tại, dùng cho cả thay đổi Sprint 3.
- `build/` và `dist/` là đầu ra build; sửa source, không sửa các file này để triển khai tính năng.

## Quy ước làm việc

- Giao tiếp và đặt tên commit bằng tiếng Việt. Không thêm tiền tố `feat`, `fix`, `docs` nếu người dùng không yêu cầu.
- Khi được yêu cầu commit riêng từng phần, tách database, backend, FE và thay đổi bố cục thành các commit có phạm vi rõ ràng.
- Sau khi một commit đã được tạo, thay đổi mới nên có commit riêng. Chỉ amend, đổi tên hoặc tách lịch sử khi người dùng yêu cầu.
- Không suy ra yêu cầu push từ yêu cầu sửa code hoặc commit. Khi người dùng yêu cầu push, dùng đúng remote/nhánh đã xác nhận; không tự đổi sang `main`.
- Giữ thay đổi sẵn có của người dùng; kiểm tra `git status` trước khi stage. Không commit file tạm hoặc đầu ra build.
- Chỉ thêm file kiểm thử hay tài liệu sử dụng khi được yêu cầu hoặc phạm vi công việc cần chúng. Có thể dùng các kiểm tra sẵn có để xác minh thay đổi.
- Không thêm dữ liệu mẫu để che lỗi API. Phân biệt dữ liệu rỗng, lỗi kết nối, thiếu quyền và hết phiên.
- Dữ liệu nghiệp vụ phải lưu qua API vào PostgreSQL. Không dùng `localStorage`, `sessionStorage` hoặc mảng JavaScript làm nguồn lưu chính thức cho chính sách, sản phẩm hay đơn hàng.
- Có thể dùng bộ nhớ tạm cho form và trạng thái giao diện. Dấu hiệu phiên hiện có trong `sessionStorage` chỉ phục vụ FE; xác thực và quyền phải dựa vào phiên ở server. Không tự xóa cơ chế này khi công việc không yêu cầu thay đổi đăng nhập.
- Với phần backend đang sửa, kiểm tra xác thực, quyền và quyền truy cập từng bản ghi; không tin ID, giá tiền hoặc vai trò do FE gửi lên.
- Khi đổi API, cập nhật mô tả hợp đồng API tại nơi dự án đang duy trì, nếu có. Nếu chưa có tài liệu API, báo rõ thay đổi request/response trong kết quả hoặc mô tả commit/PR; không tự tạo bộ tài liệu sử dụng ngoài phạm vi yêu cầu.
- Báo rõ phần đã thực hiện, đã kiểm tra và giới hạn thực tế; không nói đã push hoặc đã chạy khi chưa xác minh.

## Xác minh thay đổi

Từ thư mục gốc, build bằng `./QuanLyBanHangKho/scripts/build-sprint2.ps1`. Tham số `-JavaHome` và `-TomcatHome` cho phép đổi đường dẫn theo máy; không cài lại runtime nếu máy đã có.

Với sửa FE nhỏ, kiểm tra cú pháp và màn hình liên quan. Với thay đổi nghiệp vụ/database, xác minh trường hợp hợp lệ và lỗi có ảnh hưởng đến dữ liệu. Không chạy test tích hợp vào database thật khi chưa biết cơ chế cô lập hoặc rollback.
