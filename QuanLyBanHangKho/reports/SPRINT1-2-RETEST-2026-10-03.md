# Báo cáo kiểm thử lại Sprint 1–2 — 03/10/2026

## Kết luận

Đã phát hiện, tái hiện và sửa **5 lỗi**. Sau sửa, **254 kiểm tra/bài kiểm thử đều đạt** trong phạm vi chạy cục bộ bên dưới. Mỗi lỗi được tách một commit riêng. Không dùng con số này để suy ra 100% coverage hoặc đã nghiệm thu toàn bộ backlog.

| Bộ kiểm thử | Kết quả cuối |
|---|---:|
| Tích hợp Java/PostgreSQL `Sprint2SelfTest` | 62/62 |
| Quyền `PhanQuyenSelfTest` | 5/5 |
| Frontend Node VM, giả lập DOM/API | 15/15 |
| API thực trên Tomcat: tài khoản, quyền, hồ sơ, danh mục | 143/143 |
| API thực: XLSX, token, ảnh, xác nhận dữ liệu | 23/23 |
| Hồi quy chuyên biệt cho 5 lỗi | 6/6 |
| **Tổng** | **254/254** |

Build Java và đóng gói WAR thành công. Các kiểm tra API gọi `http://localhost:8080/QuanLyBanHangKho/`, sử dụng PostgreSQL của môi trường phát triển. Browser smoke test dùng trình duyệt trong ứng dụng.

## Các lỗi và kết quả sửa

### F01 — Mật khẩu tạm vẫn truy cập API nghiệp vụ (cao)

- Tái hiện: Admin tạo người dùng → đăng nhập bằng mật khẩu tạm → gọi `GET /api/products/`.
- Trước sửa: trả `200` dù `requiresPasswordChange=true`.
- Sau sửa: trả `403`, yêu cầu đổi mật khẩu; chỉ cho phép kiểm tra phiên, đổi mật khẩu và đăng xuất trong nhóm API cần đăng nhập. Endpoint công khai được so khớp đúng đường dẫn thay vì tìm chuỗi con.
- Giao diện tự mở màn hình Đổi mật khẩu; điều hướng sang chức năng khác vẫn bị chặn. Đã đổi version URL JavaScript để trình duyệt tải bản sửa.
- Người tự đăng ký bằng mật khẩu do chính họ chọn không bị đánh dấu là đang dùng mật khẩu tạm.
- Hồi quy: truy cập API trước/sau đổi mật khẩu, profile, đường dẫn chứa chuỗi `api/auth/login`, tự đăng ký và điều hướng frontend.

### F02 — Admin mở khóa nhưng tài khoản vẫn bị khóa tạm (vừa)

- Tái hiện: nhập sai 5 lần → Admin gọi `/api/users/{id}/unlock` → đăng nhập bằng mật khẩu đúng.
- Trước sửa: unlock trả `200`, nhưng đăng nhập vẫn trả `423`.
- Sau sửa: thao tác khóa/mở khóa reset bộ đếm sai và thời hạn khóa tạm; đăng nhập lại thành công. Thử sai một lần sau mở khóa không lập tức bị khóa lại.

### F03 — Đổi vai trò nhưng phiên cũ giữ quyền cũ (cao)

- Tái hiện: đăng nhập tài khoản SALES_MANAGER → Admin đổi sang CUSTOMER → phiên cũ tải mẫu sản phẩm.
- Trước sửa: phiên cũ vẫn trả `200`.
- Sau sửa: cập nhật tài khoản tăng `session_version` trong cùng transaction; phiên cũ trả `401`. Đăng nhập mới nhận CUSTOMER và không được tạo sản phẩm (`403`).

### F04 — Chấp nhận vai trò không tồn tại (vừa)

- Tái hiện: Admin tạo tài khoản với `roles:["NOT_A_ROLE"]`.
- Trước sửa: trả `201`, tài khoản được tạo nhưng không có vai trò, không sử dụng được.
- Sau sửa: kiểm tra vai trò và kho tồn tại/đang hoạt động trước khi gán; trả `400`, rollback toàn bộ. Cập nhật với vai trò sai không làm mất vai trò cũ.
- Hồi quy có cả mã kho không tồn tại và kiểm tra không để lại tài khoản tạo dở.

### F05 — Thiếu kiểm tra dữ liệu khi tạo người dùng (vừa)

- Tái hiện: Admin tạo người dùng với email không có định dạng email.
- Trước sửa: trả `201`.
- Sau sửa: lớp lưu người dùng kiểm tra email, username, họ tên và số điện thoại; áp dụng cho cả Admin tạo và tự đăng ký. Cập nhật thông tin cũng kiểm tra tên/điện thoại.
- Hồi quy: email sai, tên trắng, username sai ký tự, điện thoại sai đều trả `400` và không lưu tài khoản.

## Chi tiết Sprint 1

| Chức năng | Đã chạy và quan sát | Kết quả |
|---|---|---|
| Đăng nhập | 7 vai trò; tài khoản không tồn tại; mật khẩu sai; tài khoản bị khóa | Đạt |
| Tự đăng ký | Tạo thành công; chỉ nhận CUSTOMER; trùng tài khoản; mật khẩu yếu; dữ liệu sai | Đạt sau F05 |
| Mật khẩu tạm | Có cờ yêu cầu đổi; chặn API; đổi thành công mới truy cập nghiệp vụ | Đạt sau F01 |
| Đổi mật khẩu | Mật khẩu yếu/sai mật khẩu hiện tại; đổi đúng; giữ phiên hiện tại và thu hồi phiên khác | Đạt |
| Quên/đặt lại mật khẩu | Phản hồi chung với email không tồn tại; thư vào KhoMail; URL đúng context; token đúng/sai/dùng lại | Đạt |
| Đăng xuất | Đăng xuất thành công; `/auth/me` sau đăng xuất trả 401 | Đạt |
| Khóa 5 lần sai | Năm lần trả 401; đăng nhập tiếp theo bị chặn 423 | Đạt |
| Admin khóa/mở khóa | Bắt buộc lý do; thu hồi phiên; chặn đăng nhập; mở khóa tạm thành công | Đạt sau F02 |
| Quản lý người dùng | Tạo; đọc; tìm kiếm; phân trang; cập nhật vai trò; chặn tự khóa/tự xóa; xóa tài khoản chưa sử dụng và bảo vệ tài khoản có nhật ký trong Java test | Đạt sau F03–F05 |
| Phân quyền | Ma trận 7 vai trò × sản phẩm/hồ sơ/người dùng/nhật ký/mẫu Excel/NCC; anonymous bị chặn | Đạt |
| Giao diện | Menu theo quyền, dữ liệu rỗng/lỗi, escape HTML, phân trang/tìm kiếm, phản hồi đến muộn, chống submit đổi mật khẩu lặp | Đạt trong Node VM |

## Chi tiết Sprint 2

| Story | Phạm vi đã kiểm tra | Kết quả |
|---|---|---|
| S2-01 Nhập người dùng Excel | Tải mẫu theo quyền; đọc XLSX Unicode; preview không ghi; 1 dòng hợp lệ + 1 lỗi thiếu kho; commit bỏ dòng lỗi; trùng user; thư mật khẩu tạm; đăng nhập sau nhập | Đạt |
| S2-02 Hồ sơ | Đọc; sửa họ tên Unicode/điện thoại +84; đọc lại dữ liệu; chặn đổi vai trò; từ chối tên trống/điện thoại sai | Đạt |
| S2-03 Ảnh đại diện | PNG/JPG thật; ảnh 256×256; thumbnail 64×64; ảnh giả; file lớn hơn 2 MB; lưu và đọc qua API | Đạt |
| S2-04 Nhật ký | Actor và trước/sau trong transaction; lọc actor/đối tượng; lọc ngày; ngày sai; ẩn giá vốn với Admin | Đạt trong các ca đã chạy |
| S2-05 Sản phẩm | Tạo/đọc/sửa; SKU trùng; sửa tên/trạng thái; giá âm; Admin không ghi/đọc giá vốn; SALES_MANAGER đọc giá vốn; ảnh sản phẩm; đại lý không thấy sản phẩm ngừng hoạt động | Đạt |
| S2-06 Nhóm hàng | Cây 3 cấp; chống vòng lặp; chuyển nhóm sản phẩm; chặn xóa nhóm có con/có sản phẩm | Đạt |
| S2-07 Quy đổi | Hệ số dương; đơn vị cơ sở hệ số 1, không đổi; sửa tạo phiên bản; dòng đơn/phiếu nhập lấy hệ số phía server; lịch sử không đổi; chặn sửa hệ số lịch sử | Đạt |
| S2-08 Nhập sản phẩm Excel | Preview tạo/cập nhật SKU; không ghi trước xác nhận; dòng lỗi; commit từng dòng; cập nhật SKU; từ chối công thức và `.xls`; Admin không nhập cột giá vốn; token gắn phiên và dùng một lần | Đạt |
| S2-09 Nhà cung cấp | Tạo và đọc qua API; quyền Admin/kho; lưu các trường bắt buộc; không xóa NCC đã có phiếu nhập trong Java integration | Đạt |
| S2-10 Bảng giá | Tạo; trùng kỳ bị chặn; giá sàn không vượt giá bán; bảng có đơn không sửa/xóa; tạo phiên bản giữ dữ liệu cũ; đại lý chưa gán nhóm không thấy bảng giá | Đạt |

## Bằng chứng và chạy lại

- [143 kết quả API sau sửa](api-retest-results.json).
- [23 kết quả Excel/ảnh sau sửa](import-retest-results.json).
- [Kết quả API trước sửa: 138 đạt, 5 lỗi](api-before-fixes.json).
- [Kết quả Excel/ảnh trước sửa](import-before-fixes.json).
- [Hướng dẫn runner và dọn dữ liệu](../test/retest/README.md).
- [Ảnh giao diện buộc đổi mật khẩu](password-gate.png).

Runner dùng namespace QA riêng và đã dọn tài khoản, sản phẩm, nhóm, bảng giá, đơn vị, nhà cung cấp, thư và nhật ký thuộc namespace đó. Truy vấn cuối xác nhận không còn người dùng QA. Các test Java dùng rollback; sequence ID PostgreSQL vẫn tăng sau rollback, đây là hành vi bình thường.

Không đổi mật khẩu tài khoản Admin đang có. Đã xác minh Admin đăng nhập bằng mật khẩu người dùng cung cấp. Lần thử mật khẩu demo theo tài liệu không thành công là khác biệt cấu hình tài khoản, không được tính là lỗi chức năng.

## Những phần chưa xác minh đầy đủ

- Chưa có backlog/acceptance criteria đầy đủ của Sprint 1; phạm vi lấy từ `SPRINT1.md`, `SPRINT2.md`, `PROJECT_SETUP.md` và mã nguồn. Không xác nhận nghiệm thu PO.
- Frontend 15 bài dùng DOM/API giả lập, không thay thế end-to-end trên mọi trình duyệt. Browser smoke test chỉ xác minh đăng nhập Admin, mở danh mục và màn hình buộc đổi mật khẩu; chưa thao tác mọi nút trên mọi màn hình.
- Chưa chờ hết 15 phút của khóa/preview hoặc 30 phút của reset/session; chưa kiểm thử tải, đồng thời nhiều request, giới hạn 5.000 dòng/8 MB, mọi kích thước màn hình, và phân trang nhật ký trên hơn 50 bản ghi.
- Đã kiểm tra đại lý chưa có nhóm; chưa chạy HTTP end-to-end cho toàn bộ tổ hợp đại lý có nhóm, bảng giá hiệu lực/hết hiệu lực và nhiều vai trò.
- SMTP ngoài hệ thống chưa có; email được kiểm tra qua KhoMail phát triển.
- Các workflow tạo đơn, nhập/xuất kho, công nợ và báo cáo sprint sau không thuộc phạm vi; chỉ kiểm tra bảng nền dùng bảo vệ tham chiếu/quy đổi.

Không có lỗi còn thất bại trong các ca đã chạy. Các giới hạn trên là phần chưa kiểm tra, không được đánh dấu đạt thay.
