# Bộ chạy demo dành cho giảng viên

Bộ này tạo **database riêng** `khoflow_demo`, chạy migration S1–S8 rồi nạp dữ liệu minh hoạ vào PostgreSQL. Ứng dụng vẫn dùng API và dữ liệu lưu thật trong database demo; không bật dữ liệu giả dự phòng trên giao diện. Không cần cài `psql` hay chạy từng file SQL.

## Chuẩn bị

- Windows với PowerShell, JDK **17**, Node.js (để build asset), PostgreSQL **15 trở lên** và Tomcat **10.1**.
- Tài khoản PostgreSQL có quyền tạo database, schema và extension `unaccent` (tài khoản `postgres` trên máy demo phù hợp).
- Giải nén một Tomcat riêng cho demo, chưa có ứng dụng KhoFlow khác; dừng Tomcat trước khi khởi tạo/chạy. Cổng mặc định là 8080, phải còn trống.
- Mở PowerShell tại thư mục `QuanLyBanHangKho`. Thay đường dẫn Java/Tomcat dưới đây theo máy giảng viên; đường dẫn có khoảng trắng vẫn dùng được.

## 1. Tạo database và dữ liệu

```powershell
./scripts/setup-lecturer-demo.ps1 -JavaHome 'C:/Program Files/Java/jdk-17' -TomcatHome 'C:/Tools/apache-tomcat-10.1'
```

Script hỏi mật khẩu PostgreSQL bằng ô nhập ẩn. Có thể truyền `-DatabaseUser`, `-DatabaseHost`, `-DatabasePort` nếu khác `postgres`, `localhost`, `5432`. Nếu đã có biến môi trường `DB_PASSWORD`, script sử dụng biến đó và không hỏi lại. Không ghi mật khẩu PostgreSQL vào source hay file cấu hình.

Chỉ khi xuất hiện `DEMO_READY` mới hoàn tất. Script từ chối database đã tồn tại, không tự xoá/reset hoặc nạp vào `quanlybanhangkho`. Để có bộ mới, truyền `-DatabaseName khoflow_demo_lan2` ở **cả hai lệnh** tạo và chạy. Ngày tháng được tính theo ngày khởi tạo, vì vậy nên tạo bộ mới trước buổi trình bày thay vì dùng dữ liệu nhiều tháng trước.

Nếu lỗi giữa chừng, database chưa hoàn tất được giữ lại để xem nguyên nhân; sửa cấu hình rồi dùng tên demo mới. Bảng `lecturer_demo_installation.completed` chỉ bằng `true` sau khi nạp và xác minh xong.

## 2. Chạy ứng dụng

```powershell
./scripts/start-lecturer-demo.ps1 -JavaHome 'C:/Program Files/Java/jdk-17' -TomcatHome 'C:/Tools/apache-tomcat-10.1'
```

Script hỏi mật khẩu PostgreSQL, chép WAR đã build vào Tomcat và chạy Tomcat ngay trong terminal. Mở [KhoFlow](http://localhost:8080/QuanLyBanHangKho/), giữ terminal mở; `Ctrl+C` để dừng. Các biến kết nối chỉ áp dụng cho tiến trình demo, được khôi phục khi script kết thúc. Không dùng `setenv.bat` ghi đè `DB_URL`, `DB_USER`, `DB_PASSWORD` hoặc `MAIL_MODE` trên Tomcat demo.

Ảnh chứng từ minh hoạ được lưu ở `build/lecturer-demo/<database>/delivery-proofs`, ngoài WAR. Giữ thư mục này nếu chép bộ demo sang vị trí khác; tốt nhất chạy lệnh khởi tạo ngay trên máy trình bày. Ảnh có chữ DEMO, không phải chữ ký hay chứng từ thực.

## Tài khoản

Mật khẩu chung **chỉ trong database demo**: `Demo@123`. Nhập email dưới đây trên màn hình đăng nhập:

| Vai trò | Email |
|---|---|
| Quản trị | `admin@khoflow.local` |
| Quản lý kinh doanh | `sales.manager@khoflow.local` |
| Nhân viên kinh doanh | `sales@khoflow.local` |
| Quản lý kho | `warehouse.manager@khoflow.local` |
| Nhân viên kho | `warehouse@khoflow.local` |
| Kế toán | `accountant@khoflow.local` |
| Đại lý An Bình | `customer@khoflow.local` |

Email dùng miền `.local` cho bộ demo. `MAIL_MODE=dev`: thư xuất hiện trong KhoMail, không gửi email ra ngoài.

## Dữ liệu và kịch bản trình bày

- S3–S4: nhóm hàng, 6 SKU hàng tiêu dùng, đơn vị Cái/Thùng, bảng giá còn hiệu lực, bậc chiết khấu; 3 đại lý có nhân viên phụ trách, điểm giao và kho phục vụ. Đại lý An Bình liên kết tài khoản cổng đại lý.
- S5–S6: 3 kho HCM/HN/DN, vị trí kệ, kho hàng lỗi, phiếu nhập đã xác nhận, tồn theo lô và lô sắp hết hạn; phiếu soạn, xuất kho và giao hàng có ảnh minh hoạ. Có đơn ở trạng thái Nháp, Đã duyệt, Đang soạn, Đã xuất để tiếp tục thao tác.
- S7: 9 hoá đơn từ các đơn đã giao, 3 khoản thu một phần, hoá đơn chưa đến hạn và quá hạn. Mỗi đại lý có hoá đơn phát hành hôm nay, 45 ngày trước và 100 ngày trước; hạn tín dụng 30 ngày nên có nợ quá hạn 15 và 70 ngày. Ngày hoá đơn lịch sử phục vụ báo cáo tuổi nợ, ngày vận hành kho/giao hàng là ngày khởi tạo.
- S8: 2 phiếu trả đã nhận (hàng tốt/hàng lỗi) kèm giảm trừ công nợ, 1 phiếu trả chờ nhận; 1 phiếu điều chỉnh tổn thất đã duyệt; chỉ tiêu tháng hiện tại và tháng tiếp theo. Báo cáo đọc dữ liệu từ các chứng từ này.

Đăng nhập **nhân viên kho** để soạn/xuất, **quản lý kho** để nhận trả/duyệt tổn thất, **kế toán** để xem tuổi nợ, thu tiền và xuất sổ; **quản lý kinh doanh** để xem doanh số, dashboard và chỉ tiêu. Admin không tự động có mọi quyền nghiệp vụ.

Các đại lý có nợ quá hạn sẽ bị chặn tạo/gửi đơn mới theo chính sách thật của ứng dụng. Có thể trình bày chức năng chặn nợ này, sau đó dùng kế toán thanh toán đủ nợ quá hạn để mở lại việc đặt hàng. Các đơn đang xử lý đã được tạo trước khi nạp công nợ lịch sử.

## Phạm vi và khôi phục

Đây là bộ dữ liệu minh hoạ được tạo riêng theo yêu cầu bàn giao, không phải bản sao dữ liệu đang sử dụng. Các trạng thái chuyển kho, kiểm kê và đối chiếu công nợ có thể tạo tiếp trên giao diện; bộ này không tạo sẵn mọi biến thể của mọi task. Số liệu doanh số tháng trước có thể bằng 0 vì các đơn được thao tác lúc cài, dù hoá đơn lịch sử có ngày cũ.

Chạy lại trên database đã tồn tại bị từ chối để giữ nguyên thao tác giảng viên đã làm. Không cần chạy `database/demo-data.sql` cũ cùng bộ này. Danh sách migration có thứ tự tại `scripts/lecturer-migrations.txt`; chương trình nạp dữ liệu ở `scripts/SetupLecturerDemo.java` chỉ được biên dịch vào `build/demo-classes`, không đóng gói thành API trong WAR.
