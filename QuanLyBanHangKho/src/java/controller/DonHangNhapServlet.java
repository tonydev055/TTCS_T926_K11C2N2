package controller;

import db.DonHangNhapDB;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import service.ChinhSachChietKhauService;
import service.TaoDonHangService;
import util.XuLyJson;

/**
 * API tạo đơn hàng cho đại lý (S3-09). Cần quyền orders.own (nhân viên kinh doanh).
 *   GET  /api/sales-orders              danh sách đơn nháp của tôi
 *   GET  /api/sales-orders/{id}         mở lại một đơn nháp
 *   GET  /api/sales-orders/customers?q= tìm đại lý
 *   GET  /api/sales-orders/products?customerId=&q=  tìm sản phẩm theo mã hoặc tên
 *   POST /api/sales-orders/quote        tính tiền hàng, chiết khấu, phải thu (không lưu)
 *   POST /api/sales-orders              lưu nháp mới
 *   PUT  /api/sales-orders/{id}         lưu nháp đang sửa
 */
@WebServlet("/api/sales-orders/*")
public class DonHangNhapServlet extends CoSoServlet {

    private final DonHangNhapDB dao = new DonHangNhapDB();
    private final TaoDonHangService service = new TaoDonHangService();

    private static long userId(HttpServletRequest q) {
        HttpSession s = q.getSession(false);
        return ((Number) s.getAttribute("userId")).longValue();
    }

    private static String path(HttpServletRequest q) {
        String p = q.getPathInfo();
        return p == null || p.isEmpty() ? "/" : p;
    }

    private static long parseId(String text) {
        try {
            long id = Long.parseLong(text);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Mã không hợp lệ");
        }
    }

    private void fail(HttpServletResponse r, Exception e) throws IOException {
        if (e instanceof IllegalArgumentException || e instanceof IOException) error(r, 400, e.getMessage());
        else if (e instanceof NoSuchElementException) error(r, 404, e.getMessage());
        else {
            // Ghi chi tiết lỗi vào log của Tomcat để dễ tìm nguyên nhân; người dùng chỉ thấy thông báo chung.
            java.util.logging.Logger.getLogger(DonHangNhapServlet.class.getName())
                .log(java.util.logging.Level.SEVERE, "Lỗi khi xử lý /api/sales-orders", e);
            error(r, 500, "Không thể xử lý yêu cầu, vui lòng thử lại");
        }
    }

    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = path(q);
            long uid = userId(q);
            if (p.equals("/")) {
                ok(r, jsonRows(dao.listDrafts(uid)));
            } else if (p.equals("/customers")) {
                ok(r, jsonRows(dao.customers(q.getParameter("q"))));
            } else if (p.equals("/products")) {
                long customerId = parseId(Objects.toString(q.getParameter("customerId"), ""));
                if (!dao.isAgent(customerId)) throw new NoSuchElementException("Không tìm thấy đại lý");
                Long priceListId = dao.currentPriceListId(customerId);
                if (priceListId == null) throw new IllegalArgumentException(
                    "Đại lý chưa có bảng giá còn hiệu lực. Hãy kiểm tra nhóm khách hàng và bảng giá của đại lý.");
                ok(r, jsonRows(dao.products(q.getParameter("q"), priceListId)));
            } else {
                var order = dao.load(parseId(p.substring(1)), uid);
                if (order == null) throw new NoSuchElementException("Không tìm thấy đơn nháp");
                ok(r, XuLyJson.stringify(order));
            }
        } catch (Exception e) {
            fail(r, e);
        }
    }

    protected void doPost(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = path(q);
            long uid = userId(q);
            Map<String, Object> in = XuLyJson.object(body(q));
            if (p.equals("/quote")) {
                if (in.get("customerId") == null) throw new IllegalArgumentException("Vui lòng chọn đại lý");
                long customerId = ChinhSachChietKhauService.positiveId(in.get("customerId"));
                ok(r, XuLyJson.stringify(service.tinhTien(customerId, in.get("lines"))));
            } else if (p.equals("/")) {
                long id = service.luuNhap(null, uid, in);
                created(r, XuLyJson.stringify(dao.load(id, uid)));
            } else {
                throw new NoSuchElementException("Không tìm thấy chức năng");
            }
        } catch (Exception e) {
            fail(r, e);
        }
    }

    protected void doPut(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = path(q);
            if (p.equals("/")) throw new IllegalArgumentException("Thiếu mã đơn");

            long uid = userId(q);
            long id = parseId(p.substring(1));

            if (p.endsWith("/complete")) {
                String rawId =
                    p.substring(
                        1,
                        p.length() - "/complete".length()
                    );

                id = parseId(rawId);

                dao.completeOrder(id, uid);

                ok(
                    r,
                    XuLyJson.stringify(
                        dao.load(id, uid)
                    )
                );

                return;
            }

            service.luuNhap(
                id,
                uid,
                XuLyJson.object(body(q))
            );

            ok(
                r,
                XuLyJson.stringify(
                    dao.load(id, uid)
                )
            );
        } catch (Exception e) {
            fail(r, e);
        }
    }
}
