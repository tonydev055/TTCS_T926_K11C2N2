---
name: khoflow-frontend-design
description: Thiết kế và sửa FE KhoFlow theo khung ứng dụng, module chức năng và phong cách hiện có; dùng khi làm form, danh sách, menu, khoảng cách hoặc kết nối API của dự án này.
---

# FE và thiết kế KhoFlow

## Tổ chức module

FE hiện dùng HTML, CSS và JavaScript thuần. Tham khảo `web/ho-so/index.html`, `web/nguoi-dung/index.html` và `web/chiet-khau/index.html`.

- Tạo chức năng trong `web/<ten-chuc-nang>/index.html`, cùng cách nạp với các module hiện có.
- Module chứa `<style>` và `<script>`; render nội dung vào `moduleContent` khi người dùng mở trang.
- `assets/shared/feature-loader.js` chỉ nạp style/script từ fragment, không tự chèn toàn bộ body. Không truy cập DOM riêng của module trước khi render.
- Đăng ký `data-feature` trong `web/index.html`, ánh xạ chức năng trong `navigation.js`, và gọi render từ `routes.js`.
- Giữ sidebar, header, thông tin tài khoản và đăng xuất trong khung ứng dụng chung. Không tạo sidebar thay thế hoặc dùng iframe cho module cùng ứng dụng.
- Scope CSS dưới class/ID riêng của chức năng; không reset `body`, `:root`, `button`, `table` hoặc `.sidebar` toàn ứng dụng.
- Dùng tên hàm/biến riêng hoặc namespace để tránh đè tiện ích shared. Nếu dùng handler inline, namespace phải khớp ở cả HTML ban đầu và HTML tạo động.

## Thiết kế đồng bộ

- Đọc `assets/shared/styles.css` và một màn hình tương tự trước khi chọn bố cục.
- Giữ phong cách KhoFlow: nền sáng, card trắng, chữ xanh đậm, màu cam cho thao tác chính; ưu tiên token/class hiện có.
- Dùng khoảng cách có hệ thống: 8–12px giữa nút, 16–24px giữa nhóm nội dung; theo màn hình lân cận khi có mẫu rõ hơn.
- Tách tiêu đề/mô tả, tab, thông báo và bảng thành nhóm; đặt `gap` trên đúng container chứa các nhóm, tránh các phần dính sát nhau.
- Nút thao tác cùng hàng có `gap`, cho phép wrap khi thiếu chiều ngang; trạng thái không dính vào nút.
- Form có label, dấu bắt buộc, lỗi tại trường; giữ giá trị người dùng khi server trả lỗi. Không báo lưu thành công trước khi API xác nhận.
- Bảng rộng nằm trong vùng cuộn ngang của nội dung; trên màn hình nhỏ, form và toolbar có thể xuống hàng mà không đẩy menu ra ngoài viewport.
- Nội dung hiển thị bằng tiếng Việt; không đưa tên class, SQL hay hướng dẫn kết nối backend vào màn hình người dùng.

## Thiết kế theo công việc của người dùng

- Với màn hình mới, xác định ai sử dụng, thao tác chính là gì và dữ liệu nào cần thấy trước. Trong KhoFlow, ưu tiên nhập liệu, tra cứu và quản lý hơn bố cục hero của trang quảng cáo.
- Với yêu cầu chỉnh một form/khoảng cách, lấy màn hình hiện có làm chuẩn; không đổi palette, font, header hoặc thêm hiệu ứng chỉ để tạo cảm giác khác biệt.
- Với yêu cầu thiết kế mới hoặc redesign, phác bố cục và chọn token màu, chữ, khoảng cách ngắn gọn trước khi code. Dùng token hiện có nếu phù hợp; chỉ đổi hướng thị giác khi yêu cầu cho phép.
- Typography có phân cấp rõ giữa tiêu đề, label, nội dung và số liệu. Giữ font hỗ trợ tiếng Việt, line-height dễ đọc; tránh giãn ký tự quá mức hoặc viết hoa mọi label nếu không có lý do từ thiết kế hiện tại.
- Viền, card, divider và nhãn phải giúp phân nhóm thông tin. Không chia mọi nội dung thành các card giống nhau hoặc thêm shadow/gradient chỉ để trang trông nhiều chi tiết.
- Không thêm số thứ tự nếu nội dung không phải chuỗi bước. Một thao tác chính nên có ưu tiên thị giác rõ; thao tác phụ và thao tác xóa dùng mức nhấn phù hợp.
- Chuyển động phục vụ phản hồi sau thao tác như mở form, xác nhận hoặc mở rộng nội dung; tránh tự chạy nhiều animation khi tải trang. Tôn trọng `prefers-reduced-motion` khi có animation.

## Khả năng sử dụng và nội dung

- Giữ focus bàn phím nhìn thấy được, liên kết label với input và đặt tên truy cập cho nút chỉ có icon. Dùng button cho thao tác và link cho điều hướng.
- Không dùng màu làm dấu hiệu duy nhất cho lỗi hoặc trạng thái; bổ sung chữ/icon có ý nghĩa. Thông báo lưu/lỗi có thể dùng `role="status"` hoặc `aria-live` phù hợp, tránh thông báo lặp gây nhiễu.
- Kiểm tra form/bảng ở chiều rộng desktop và điện thoại; vùng cuộn ngang của bảng không được làm toàn trang tràn ngang.
- Nút nói rõ việc sẽ xảy ra, ví dụ “Lưu chính sách”; thông báo thành công dùng cùng cách gọi “Đã lưu chính sách”. Không đổi thuật ngữ giữa các bước của một luồng.
- Lỗi nói rõ nguyên nhân và cách xử lý khi biết; trạng thái rỗng có hành động tiếp theo phù hợp quyền. Không dùng lời quảng cáo hoặc nội dung trang trí thay cho hướng dẫn thao tác.
- Khi có thể, xem lại ảnh màn hình thực tế để kiểm tra khoảng cách, căn hàng, chữ dài và focus. Với chỉnh nhỏ, chỉ kiểm tra vùng liên quan; không bắt người dùng duyệt một kế hoạch thiết kế mới.

## Dữ liệu và điều hướng

- Ưu tiên `apiRequest`, `apiFetch`, `jsonRequestOptions` và xử lý phiên trong `assets/shared/api.js` khi phù hợp.
- URL API tương đối với context ứng dụng; không hardcode `/api` ở gốc host hoặc địa chỉ localhost trong module.
- Dùng dữ liệu API thật. Danh sách rỗng có trạng thái rỗng; lỗi API có thông báo lỗi, không chuyển lỗi thành dữ liệu rỗng thành công.
- Dữ liệu nghiệp vụ cần tồn tại qua lần tải lại phải được lưu bằng API/database. Không dùng `localStorage` hay `sessionStorage` thay cho việc lưu thật; bộ nhớ tạm chỉ giữ trạng thái UI hoặc form chưa lưu.
- Chặn gửi form lặp khi request đang chạy; chỉ đóng form hoặc báo thành công sau khi server xác nhận. Khi lỗi, giữ nội dung đã nhập để người dùng sửa hoặc thử lại.
- Escape dữ liệu người dùng trước khi ghép `innerHTML`; dùng `textContent` khi chỉ hiển thị chữ.
- Tránh phản hồi đến muộn ghi đè màn hình khác: kiểm tra `viewRevision` hoặc phiên render riêng trước khi cập nhật DOM.
- Điều hướng qua `openView`, giữ hash/back/forward và giới hạn menu theo quyền. Quyền FE không thay thế kiểm tra quyền backend.
- Khi sửa asset shared, chạy `node QuanLyBanHangKho/scripts/version-frontend.cjs` từ gốc để cập nhật cache version.

## Hoàn tất thay đổi

Kiểm tra màn hình liên quan, menu cũ, form thêm/sửa và trạng thái lỗi có thể bị ảnh hưởng. Không thêm thư viện UI/framework hoặc thiết kế lại toàn ứng dụng cho một yêu cầu chỉnh form nhỏ.
