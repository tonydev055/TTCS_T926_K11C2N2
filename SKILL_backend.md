---
name: khoflow-backend
description: Phát triển và sửa backend Java Servlet, PostgreSQL và phân quyền của dự án KhoFlow; dùng khi thay đổi API, dữ liệu hoặc quy tắc nghiệp vụ trong repository này.
---

# Backend KhoFlow

## Kiến trúc đang sử dụng

Java 17, Jakarta Servlet trên Tomcat 10.1 và PostgreSQL. Giữ kiến trúc hiện tại; không tự chuyển sang Spring Boot hay thêm ORM.

- `controller/CoSoServlet.java`: đọc body, trả JSON và mã HTTP.
- `util/XuLyJson.java`: parser/serializer cho JSON lồng nhau. Không dùng regex để đọc cấu trúc JSON phức tạp.
- `db/CoSoDB.java`: truy vấn JDBC với tham số.
- `util/KetNoiDB.java`: kết nối qua `DB_URL`, `DB_USER`, `DB_PASSWORD`; không sao chép mật khẩu vào hướng dẫn, log hoặc commit mới.
- `filter/XacThucFilter.java` và `security/PhanQuyen.java`: xác thực phiên và quyền API.

Đọc implementation gần chức năng đang sửa trước khi chọn cách mở rộng. Một số service/API cũ còn là khung; không coi tên class tồn tại là nghiệp vụ đã hoàn thiện.

## API và nghiệp vụ

- Servlet xử lý request/response; service giữ quy tắc nghiệp vụ; DAO giữ SQL và quản lý ghi dữ liệu.
- Validate ở server dù FE đã validate: ID, số lượng, enum, độ dài, giá trị tiền và quan hệ dữ liệu.
- Dùng `BigDecimal` cho tiền, tỷ lệ và phép tính cần chính xác. Quy định rõ đơn vị, giới hạn và cách làm tròn theo nghiệp vụ.
- Dùng `PreparedStatement`; không ghép đầu vào người dùng vào SQL.
- Thao tác ghi nhiều bảng phải cùng connection/giao dịch, commit khi toàn bộ thành công và rollback khi có lỗi.
- Mã HTTP theo ý nghĩa: 201 khi tạo, 400 dữ liệu sai, 401 hết phiên, 403 thiếu quyền, 404 không tồn tại, 500 lỗi nội bộ.
- Trả thông báo tiếng Việt hữu ích; log lỗi nội bộ tại server, không trả stack trace hoặc thông tin kết nối cho FE.
- Thêm route mới vào `PhanQuyen.permissionFor` với quyền phù hợp; kiểm tra server chặn được thao tác dù người dùng gọi API trực tiếp.

## Bảo mật trong phần đang thay đổi

- Xác minh cả quyền thao tác và phạm vi bản ghi: có quyền xem đơn hàng không đồng nghĩa được xem đơn của mọi khách hàng. Lấy danh tính từ session đã xác thực, không từ `userId` hoặc vai trò trong request.
- Với API dùng cookie phiên để ghi dữ liệu, kiểm tra cơ chế chống CSRF hiện có và dùng nhất quán; không coi POST/PUT/DELETE hay ẩn nút là đủ để bảo vệ request.
- Không trả mật khẩu, hash mật khẩu, token đặt lại mật khẩu hoặc khóa bí mật trong API/log. Dùng tiện ích mật khẩu hiện có thay vì lưu mật khẩu rõ hoặc tự tạo thuật toán mã hóa.
- Chỉ cho phép cập nhật các trường hợp lệ; không bind toàn bộ payload vào bản ghi để khách hàng tự đổi role, chủ sở hữu hay trạng thái ngoài quyền.
- Giới hạn body, phân trang và file upload phù hợp endpoint; kiểm tra loại/kích thước file ở server, không chỉ tại FE.
- Với giá và tổng tiền được dùng để ghi đơn, lấy hoặc xác minh lại từ dữ liệu có thẩm quyền tại server. Phân biệt báo giá thử bằng giá nhập với giá được phép lưu trong nghiệp vụ.
- Áp dụng các kiểm tra này cho luồng liên quan đến yêu cầu; không tự mở rộng một sửa nhỏ thành đợt thiết kế lại bảo mật toàn dự án.

## Lưu dữ liệu thật và hợp đồng API

- Ghi dữ liệu nghiệp vụ qua DAO vào PostgreSQL. Trả kết quả sau khi giao dịch đã commit, rồi để FE tải lại dữ liệu khi cần.
- Không thay backend bằng lưu browser hoặc biến trong bộ nhớ để báo tính năng đã hoạt động. Form nháp trong bộ nhớ không phải bản ghi đã lưu.
- Khi thêm/sửa endpoint, xác định method/path, quyền cần có, trường bắt buộc, kiểu dữ liệu, đơn vị, giới hạn, response và mã lỗi.
- Cập nhật tài liệu API đang tồn tại khi có thay đổi hợp đồng. Không sao chép dữ liệu khách hàng thật hoặc thông tin bí mật vào ví dụ.
- Nếu dự án chưa có tài liệu API riêng, mô tả hợp đồng thay đổi trong kết quả công việc hoặc PR phù hợp; chỉ tạo tài liệu mới khi được yêu cầu hoặc nằm trong phạm vi đã thống nhất.
- Giữ tương thích với FE đang dùng endpoint; khi phải đổi schema response, sửa caller liên quan trong cùng công việc.

## Database

- Migration nằm trong `database/`, có thứ tự phụ thuộc rõ ràng. Với database đang dùng, ưu tiên bổ sung schema thay vì xóa dữ liệu.
- Khai báo khóa ngoại, unique và check cho ràng buộc phù hợp. Tránh `DROP`, `TRUNCATE` hoặc sửa dữ liệu nghiệp vụ không thuộc yêu cầu.
- Khi danh sách không hiện đủ, đối chiếu SQL, bộ lọc `active`, phân trang và dữ liệu thật trước khi đổi hành vi.
- Không tự bỏ bộ lọc sản phẩm ngừng hoạt động chỉ để dropdown trông đầy đủ.

## Quy tắc S3-01 hiện tại

`ChinhSachChietKhauService`, `ChinhSachChietKhauDB` và `/api/discount-policies` quản lý chính sách/bậc chiết khấu. Schema tại `database/sprint3.sql`.

Trong mỗi chính sách chọn ngưỡng số lượng cao nhất đủ điều kiện; giữa các chính sách chọn mức giảm tiền cao nhất, không cộng dồn. Hòa thì chọn ID nhỏ nhất. Giảm mỗi đơn vị không vượt đơn giá, làm tròn 2 số lẻ; phần trăm không vượt 100%.

`DonHangService.tinhGiaDong` nhận số lượng theo đơn vị cơ sở và đơn giá do caller cung cấp. API quote là kiểm tra giá, không ghi đơn hàng. Không tuyên bố luồng tạo đơn đã hoàn thành nếu chưa triển khai việc lưu đơn và lấy giá có thẩm quyền.
