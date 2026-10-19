# Gửi thư bằng Gmail SMTP

Ứng dụng hỗ trợ Gmail SMTP (`smtp.gmail.com:587`, STARTTLS bắt buộc, xác minh chứng chỉ máy chủ). Dùng **App Password**, không dùng mật khẩu đăng nhập Gmail.

## Thiết lập một lần

1. Bật xác minh 2 bước cho Gmail. Tạo App Password tại https://myaccount.google.com/apppasswords. Một số tài khoản tổ chức hoặc Advanced Protection không hỗ trợ App Password; xem hướng dẫn Google bên dưới.
2. Chạy `database/smtp.sql` vào database hiện tại bằng `psql -v ON_ERROR_STOP=1` hoặc pgAdmin. Migration chỉ thêm hàng đợi email, không xóa thư KhoMail cũ.
3. Build lại: `./scripts/build-sprint2.ps1 -Test`. Thư viện Jakarta Mail và Activation đã nằm trong `web/WEB-INF/lib`.
4. Cấu hình cố định cho máy này (khuyên dùng): chạy một lần từ thư mục dự án
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\setup-gmail.ps1
   ```
   Nhập Gmail gửi thư và App Password tại lời nhắc ẩn. Script ghi `bin\setenv.bat` trong thư mục Tomcat (giữ các dòng cấu hình khác, sao lưu bản cũ thành `setenv.bat.bak`); Tomcat đọc file này mỗi lần khởi động, kể cả khi chạy từ NetBeans, nên không phải nhập lại. Sau đó dừng và chạy lại Tomcat. Tham số: `-TomcatHome` nếu Tomcat cài ở chỗ khác, `-AppUrl` cho địa chỉ máy chủ, `-Mode dev` để quay về hộp thư thử nghiệm KhoMail. App Password chỉ nằm trong thư mục Tomcat của máy đó, không nằm trong repository hay file WAR; mỗi máy cần chạy script một lần.

   Chỉ chạy tạm một phiên: dừng Tomcat đang chạy (trong NetBeans hoặc nơi bạn khởi động nó), rồi chạy `./scripts/start-gmail.ps1` từ thư mục dự án. Nhập địa chỉ Gmail và App Password tại lời nhắc ẩn. Script không lưu mật khẩu ra file hoặc Git; lần khởi động Tomcat sau sẽ quay về chế độ `dev`.
5. Nếu ứng dụng chạy trên máy chủ, truyền `-AppUrl 'https://ten-mien-cua-ban/QuanLyBanHangKho/'`. Liên kết localhost chỉ mở được trên máy đang chạy ứng dụng; người nhận dùng máy khác cần URL máy chủ truy cập được.

Script khởi chạy Tomcat riêng với môi trường Gmail. Nếu muốn chạy Tomcat từ NetBeans/service, phải cấu hình các biến bên dưới cho **đúng tiến trình khởi chạy Tomcat**, sau đó khởi động lại; Java không tự đọc file `.env`.

| Biến | Giá trị |
|---|---|
| `MAIL_MODE` | `smtp` để gửi Gmail; `dev` để chỉ lưu KhoMail, mặc định `dev` |
| `SMTP_USERNAME` | Địa chỉ Gmail dùng gửi thư |
| `SMTP_APP_PASSWORD` | App Password, có thể dán kèm khoảng trắng |
| `APP_URL` | URL ứng dụng để tạo liên kết đặt lại mật khẩu |

## Luồng hoạt động

- Admin tạo tài khoản và nhập người dùng Excel: email mật khẩu tạm được xếp hàng trong cùng transaction với tài khoản.
- Quên mật khẩu: token và email được lưu trong cùng transaction; rollback thì không có email được gửi.
- Worker gửi sau commit, đọc hàng đợi mỗi 2 giây. Mỗi lần xử lý 1 thư; thử lại sau 1 phút, tối đa 5 lần. Thư chờ quá 25 phút bị hủy để không gửi liên kết reset quá cũ.
- `SENT` nghĩa là Gmail đã chấp nhận thư, không bảo đảm thư đã vào Inbox. Kiểm tra cả Spam.
- Khi gửi thành công hoặc kết thúc thử lại, xóa nội dung/token khỏi hàng đợi. Không ghi token, mật khẩu tạm hay SMTP password vào log.
- Khi `MAIL_MODE=smtp`, API KhoMail trả 404 và nút mở hộp thư thử nghiệm bị ẩn; không sao chép email SMTP vào KhoMail. Chế độ SMTP thiếu cấu hình sẽ báo lỗi, không tự chuyển về KhoMail.
- SMTP là cơ chế gửi ít nhất một lần: nếu Gmail nhận thư nhưng kết nối/commit DB lỗi ngay sau đó, có thể có thư trùng khi thử lại.

## Kiểm tra gửi thật

Để kiểm tra riêng luồng nhắc nợ S7-08, trên nhánh task S7-08 chạy từ gốc repository:

```powershell
.\QuanLyBanHangKho\scripts\check-s7-08-mail.ps1 -Recipient 'email-ban-so-huu@example.com'
```

Script tạo database `khoflow_s7_test_YYYYMMDDhhmmss` riêng, áp dụng migration và lưu hai hóa đơn kiểm thử: 1.250.000 đồng đến hạn sau 3 ngày, 250.000 đồng đã quá hạn 7 ngày. Email nhận là tham số, không viết sẵn địa chỉ cá nhân trong repository. Nhập Gmail gửi và App Password tại lời nhắc ẩn; script chạy chính `NhacNoService.daily()` và worker SMTP, kiểm tra chạy lại trong ngày không tạo trùng. Không thay database hoặc khởi động lại Tomcat đang chạy. Chỉ kết luận gửi thành công khi cả hai hàng đợi là `SENT`; cần kiểm tra Inbox/Spam để xác nhận nhận thư. Thêm `-PrepareOnly` để chuẩn bị/xem dữ liệu mà chưa gửi. Các file trạng thái trong `build/s7-live-mail` chỉ lưu database/email nhận, không lưu mật khẩu. Dữ liệu này chỉ dành cho kiểm thử riêng, không được đưa vào database kinh doanh thật; nên thực hiện kiểm tra ngay ngày chuẩn bị dữ liệu.

Sau khi cấu hình, dùng một tài khoản thử với địa chỉ email bạn sở hữu: tạo tài khoản hoặc dùng Quên mật khẩu, kiểm tra Gmail và trạng thái:

```sql
SELECT id,status,attempts,created_at,sent_at,last_error
FROM mail_outbox ORDER BY id DESC LIMIT 20;
```

`AuthenticationFailedException`: kiểm tra App Password/xác minh 2 bước. Lỗi kết nối: kiểm tra mạng/cổng 587. Đổi mật khẩu Google có thể thu hồi App Password. Sau sửa cấu hình, khởi động lại Tomcat; với thư `FAILED`, thực hiện lại yêu cầu quên mật khẩu. Nếu email kích hoạt không tới, tài khoản đã tạo có thể dùng Quên mật khẩu để lấy quyền truy cập.

Các bài `SmtpSelfTest` kiểm tra MIME Unicode, TLS, cấu hình, queue/rollback, retry, hết hạn và xóa nội dung nhạy cảm bằng sender giả lập. Không gửi email ra Internet và không chứng minh Gmail thật đã nhận thư.

Không chạy bộ `test/retest/run-retest.ps1` trên ứng dụng đang dùng SMTP: bộ này dành riêng cho `MAIL_MODE=dev` và tài khoản `example.test`.

## Tài liệu Google

- https://support.google.com/accounts/answer/185833
- https://support.google.com/mail/answer/7104828
