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

Database mới: chạy `database/sprint1.sql`, `database/sprint2.sql`, `database/role-permissions.sql`, `database/sprint3.sql`, `database/sprint3-price-history.sql`, `database/sprint3-discount-audit.sql`, `database/sprint3-customers.sql`, `database/sprint3-delivery-addresses.sql`, `database/sprint3-orders.sql`, `database/sprint3-credit-limit.sql`, `database/sprint3-customer-assignment.sql`, `database/sprint3-customer-lock.sql`, `database/sprint3-customer-search.sql`, `database/sprint4-order-lifecycle.sql`, `database/sprint4-order-pricing.sql`, `database/sprint4-credit-check.sql`, `database/sprint4-stock-availability.sql`, `database/sprint4-order-approval.sql`, `database/sprint4-dealer-portal.sql`, rồi các migration Sprint 5 theo đúng thứ tự phụ thuộc `database/sprint5-warehouses.sql`, `database/sprint5-stock-receipts.sql`, `database/sprint5-reservation-expiry.sql`, `database/sprint5-order-tracking.sql`, `database/sprint5-stock-counts.sql`, `database/sprint5-transfers.sql`, `database/sprint5-low-stock.sql`, rồi các migration Sprint 6 theo thứ tự `database/sprint6-pick-lists.sql`, `database/sprint6-shipments.sql`, `database/sprint6-delivery-confirmation.sql`, `database/sprint6-delivery-trips.sql`, `database/sprint6-lots.sql` (file lô thuộc nhánh S6-01) trong database `quanlybanhangkho` bằng pgAdmin hoặc `psql -v ON_ERROR_STOP=1`. Database đang sử dụng cũng cần chạy các migration chưa áp dụng một lần để bật chức năng tương ứng. `sprint3-discount-audit.sql` ghi nhật ký thay đổi chính sách chiết khấu. `sprint3-customers.sql` tạo hồ sơ đại lý; `sprint3-delivery-addresses.sql` thêm điểm giao hàng; `sprint3-orders.sql` thêm các cột lưu nháp và tính tiền đơn hàng; `sprint3-credit-limit.sql` thêm hạn mức công nợ; `sprint3-customer-assignment.sql` thêm lịch sử phân công nhân viên phụ trách; `sprint3-customer-lock.sql` thêm khoá giao dịch và chặn tạo đơn cho đại lý bị khoá; `sprint3-customer-search.sql` thêm hàm `khongdau` để tìm đại lý không dấu. `sprint4-order-lifecycle.sql` thêm vòng đời đơn hàng (Nháp → Chờ duyệt → Đã duyệt → Đang soạn hàng → Đã xuất → Đã giao → Đóng, nhánh Từ chối/Huỷ), chặn bước chuyển sai, bắt buộc lý do huỷ và lưu lịch sử trạng thái chỉ thêm, không sửa/xoá; đơn ở trạng thái cũ `COMPLETED`/`CONFIRMED` được đưa về Chờ duyệt. `sprint4-order-pricing.sql` lưu giá bảng giá, giá sàn và cờ sửa giá thủ công trên từng dòng đơn, cùng danh sách lý do cần duyệt của đơn. `sprint4-credit-check.sql` thêm view `customer_credit_status` (công nợ hiện tại, phần còn lại, khoản quá hạn — tạm tính từ đơn đã duyệt chưa đóng cho tới khi có hoá đơn/thanh toán ở Sprint 7) và chặn tạo/gửi đơn mới cho đại lý có nợ quá hạn. `sprint4-stock-availability.sql` thêm kho phục vụ của đại lý, bảng tồn kho `inventory` (tồn thực tế, đang giữ chỗ) và `stock_reservations`; tồn khả dụng = tồn thực tế − đang giữ chỗ, database không cho giữ vượt tồn. Phiếu nhập kho (Sprint 5) chưa có nên tồn ban đầu bằng 0: cần gán kho phục vụ cho đại lý và có tồn trong `inventory` thì mới gửi được đơn. `sprint4-order-approval.sql` lưu lịch sử duyệt đơn (Duyệt, Từ chối, Trả lại sửa) kèm lý do và công nợ lúc quyết định; Từ chối/Trả lại bắt buộc ý kiến, lịch sử không sửa/xoá được. `sprint4-dealer-portal.sql` liên kết hồ sơ đại lý với một tài khoản vai trò Đại lý để dùng cổng đặt hàng; đơn đại lý tự đặt bắt đầu ở Chờ nhân viên xác nhận. `sprint5-warehouses.sql` thêm địa chỉ, người phụ trách kho và vị trí trong kho. `sprint5-stock-receipts.sql` thêm phiếu nhập Nháp/Đã xác nhận, lô và hạn dùng, tồn theo vị trí/lô (`stock_bins`, khởi tạo từ `inventory` hiện có) và sổ biến động `stock_movements`; phiếu đã xác nhận không sửa được. `sprint5-reservation-expiry.sql` thêm thời hạn giữ chỗ (mặc định 24 giờ) và cho phép đơn hết hạn giữ chỗ trở về Chờ duyệt. `sprint5-order-tracking.sql` lưu số lượng thực giao từng dòng. `sprint5-stock-counts.sql` thêm phiếu kiểm kê chụp tồn sổ theo lô/vị trí. `sprint5-transfers.sql` thêm phiếu chuyển kho (Nháp → Đang chuyển → Đã nhận) và khoá dòng phiếu sau khi xuất chuyển. `sprint5-low-stock.sql` lưu ngưỡng tồn tối thiểu theo sản phẩm và kho. `sprint6-pick-lists.sql` thêm phiếu soạn gộp nhiều đơn. `sprint6-shipments.sql` cho một dòng đơn xuất từ nhiều lô và liên kết đơn giao bù với đơn còn nợ hàng. `sprint6-delivery-confirmation.sql` lưu kết quả giao và ảnh chứng từ; file này thay hàm kiểm tra vòng đời đơn của `sprint5-reservation-expiry.sql`, nên nếu chạy lại file Sprint 5 đó thì chạy lại file này sau. `sprint6-delivery-trips.sql` lưu tài xế, xe, chuyến giao và thứ tự điểm giao. `sprint6-lots.sql` thêm ngày sản xuất và ngưỡng cảnh báo hạn dùng. Các migration Sprint 6 dùng index `NULLS NOT DISTINCT` nên cần PostgreSQL 15 trở lên. Các migration Sprint 5 chạy lại an toàn; database đã chạy `sprint5-transfers.sql` bản trước cần chạy lại file này để bật khoá dòng phiếu chuyển. `sprint3-price-history.sql` lưu lịch sử giá chỉ đọc; database chặn trực tiếp việc sửa hoặc xóa các bản ghi này. Không có dữ liệu sản phẩm/đơn hàng mẫu được thêm bởi Sprint 2.

Sau các migration Sprint 6, chạy `database/sprint7-sales-invoices.sql` để bật S7-01: phát hành hóa đơn theo số lượng thực giao, gộp phiếu giao cùng đại lý, cấp số liên tục và lập chứng từ điều chỉnh.

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

S7-02 (phụ thuộc S7-01): chạy `database/sprint7-payments.sql`. GET `api/payments` trả `customers`, `invoices`, `payments`; GET `api/payments/{id}` trả khoản thu và `allocations`. POST `api/payments` nhận `customerId`, `amount` (tiền, tối đa 2 số lẻ), `receivedDate` (YYYY-MM-DD, không ở tương lai), `paymentMethod` (CASH/BANK_TRANSFER/OTHER), `note`, `requestKey` (UUID), `invoiceId` tùy chọn. Không chọn hóa đơn thì đối trừ FIFO theo ngày phát hành rồi ID. POST `api/payments/{id}/reversals` nhận `reason` bắt buộc để hoàn tác toàn bộ khoản thu bằng bút toán riêng; GET vẫn giữ chứng từ gốc. PUT/DELETE bị từ chối. Quyền `payments.write`; danh tính người thu lấy từ session. Hạn thanh toán hóa đơn được chụp tại lúc phát hành. Số dư lấy từ `receivable_invoice_balances`, gồm điều chỉnh hóa đơn và khoản thu chưa hoàn tác. Không dùng bảng `payments` khung cũ hoặc bảng `cash_receipts` độc lập của bản S7-09 cũ làm nguồn công nợ.

S7-08 phụ thuộc S7-02: chạy `database/smtp.sql` rồi `database/sprint7-payment-reminders.sql`. GET `api/reminders` (receivables.read), POST `/run` đánh giá ngày hiện tại; UNIQUE invoice/ngày và bản ghi job trong PostgreSQL chống gửi trùng nhiều tab/nhiều instance. GET `api/dealer-reminders` và POST `/{id}/read` (invoices.self) chỉ đọc/đánh dấu thông báo của đại lý hiện tại. Server chạy kiểm tra mỗi phút, thực hiện một lượt/ngày cho hóa đơn còn nợ trước hạn đúng 3 ngày hoặc quá hạn. SMTP đưa vào mail_outbox và trạng thái SENT chỉ có khi gửi thành công; DEV ghi rõ DEVELOPMENT_MAILBOX, không báo đã gửi email thật; thiếu liên kết/email ghi MISSING_RECIPIENT. Chạy độc lập với trình duyệt, chỉ khi ứng dụng server đang hoạt động.
Bản hoàn thiện S7-01 phụ thuộc S7-02: đơn giá in là giá trước chiết khấu, tạm tính làm tròn từng dòng nhất quán với lúc phát hành; phát hành/điều chỉnh dùng cùng khóa đại lý với thanh toán. Chặn điều chỉnh giảm làm số dư âm sau đối trừ. API ghi hóa đơn kiểm tra JSON và nguồn cùng ứng dụng. Dữ liệu kiểm thử chỉ nằm trong database tạm của test; đã bỏ script hóa đơn mẫu khỏi database triển khai.
S7-03 phụ thuộc các commit S7-02: GET `api/receivables/aging` trả `dealers`, `summary`, `dealerCount`, `regions`; nhận bộ lọc `q`, `region`, `age` (0/31/61/91), `overdue=true`. Tuổi nợ tính từ ngày phát hành, quá hạn tính từ due_date. GET `api/receivables/export` dùng cùng bộ lọc, trả XLSX có ô số và tổng cộng. Quyền `receivables.read`.
S7-04 phụ thuộc S7-02: chạy `database/sprint7-credit-risk.sql`. View `customer_credit_status` dùng số dư hóa đơn cộng đơn đã cam kết chưa xuất hóa đơn để không tính trùng. GET/POST `api/credit-alerts`, quyền `customers.credit`; POST nhận `overLimitMode` và `overdueMode` (BLOCK/REVIEW). Database kiểm tra lại khi tạo/gửi đơn; REVIEW gắn cờ cần duyệt. Cờ công nợ cập nhật sau phân bổ/hoàn tác thu tiền, thay đổi hóa đơn/đơn/đại lý và tác vụ server mỗi ngày; không phụ thuộc mở trình duyệt. Các kênh lập đơn cần áp dụng migration này cùng code S7-04.
S7-05 phụ thuộc S7-02: chạy `database/sprint7-route-collections.sql`. GET/POST multipart `api/route-collections` (orders.own) nhận customerId/amount/collectedDate/note/requestKey/proof JPG/PNG <=2MB; chỉ thu đại lý được phân công. GET `/{id}/proof` chỉ người thu hoặc kế toán được xem. Khoản thu lưu PENDING, chưa đối trừ. GET `api/collection-settlements` và POST `/{id}/accept` (payments.write) xác nhận nộp tiền, tạo khoản thu/đối trừ FIFO và chuyển RECEIVED trong một transaction. Gửi lặp không tạo thêm khoản thu. Bảng đối chiếu trả totals theo nhân viên/ngày. Phiếu thu tại S7-09 dùng payment_id của khoản đã nhận. Nếu công nợ đã thay đổi khiến không thể đối trừ đủ thì toàn bộ xác nhận rollback và khoản thu vẫn chờ nộp để kế toán xử lý.
S7-06 phụ thuộc S7-02: chạy `database/sprint7-reconciliations.sql`. GET/POST `api/reconciliations` (receivables.read), POST nhận customerId/from/to; một biên bản duy nhất mỗi đại lý/kỳ, lưu snapshot bất biến. GET `api/reconciliations/{id}` xem chi tiết. Đại lý dùng GET `api/dealer-reconciliations` và GET `/{id}`, POST `/{id}/response` nhận status CONFIRMED hoặc DISPUTED; DISPUTED bắt buộc reason và dealerBalance. Quyền invoices.self, kiểm tra sở hữu tại server. Phản hồi chỉ ghi một lần, không tự sửa số dư. Bản in/PDF chứa số liệu snapshot và người/thời điểm phản hồi.
S7-07 phụ thuộc S7-02: GET `api/dealer-debt` trả `summary` và `invoices` của đại lý liên kết với userId trong session; GET `api/dealer-debt/{invoiceId}` chỉ trả hóa đơn của chính đại lý đó (404 nếu ngoài phạm vi). Quyền `invoices.self`. Số dư/hạn mức lấy từ hóa đơn, khoản thu, điều chỉnh và đơn đã cam kết chưa có hóa đơn; không nhận customerId từ client.
S7-09 phụ thuộc S7-02: GET `api/cash-receipts` và `api/cash-receipts/{id}` đọc chính khoản thu đã đối trừ từ S7-02. POST bị từ chối (405), không tạo thêm khoản thu độc lập. Phiếu có mã duy nhất, ngày, đại lý, hình thức, người thu và phân bổ hóa đơn; phiếu hoàn tác có dấu trạng thái trên bản in. Quyền `payments.write`. Không cần chạy migration `sprint7-cash-receipts.sql` cũ; nếu đã có bảng cũ, giữ dữ liệu để đối soát, không tự chuyển thành khoản thu đã đối trừ.
