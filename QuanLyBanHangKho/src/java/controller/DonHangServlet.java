package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import service.DonHangService;
import util.XuLyJson;

/**
 * Đơn hàng đã lập (S4-06). Cần orders.read (xem mọi đơn) hoặc orders.own (đơn của đại lý mình phụ trách).
 *   GET  /api/orders?status=&customerId=&salesRepId=&region=&from=&to=&page=&pageSize=
 *                                              danh sách, kèm tổng số đơn và tổng phải thu của tập kết quả đang lọc (S4-07);
 *                                              from/to (yyyy-MM-dd) lọc theo ngày tạo đơn
 *   GET  /api/orders/filters                   giá trị cho các ô lọc trong phạm vi được xem
 *   GET  /api/orders/{id}                      chi tiết, dòng hàng, lịch sử trạng thái và thao tác được phép
 *   POST /api/orders/{id}/cancel {reason}      huỷ đơn chưa xuất kho; bắt buộc lý do, nhả phần giữ chỗ
 */
@WebServlet("/api/orders/*")
public class DonHangServlet extends CoSoServlet {

    private final DonHangService service = new DonHangService();

    static long userId(HttpServletRequest q) {
        return ((Number) q.getSession(false).getAttribute("userId")).longValue();
    }

    @SuppressWarnings("unchecked")
    static List<String> roles(HttpServletRequest q) {
        Object roles = q.getSession(false).getAttribute("roles");
        return roles instanceof List<?> ? (List<String>) roles : List.of();
    }

    static String path(HttpServletRequest q) {
        String p = q.getPathInfo();
        return p == null || p.isEmpty() ? "/" : p;
    }

    static long parseId(String text) {
        try {
            long id = Long.parseLong(text);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw new NoSuchElementException("Không tìm thấy đơn hàng");
        }
    }

    static Map<String, String> params(HttpServletRequest q, String... names) {
        Map<String, String> out = new HashMap<>();
        for (String n : names) out.put(n, q.getParameter(n));
        return out;
    }

    void fail(HttpServletResponse r, Exception e) throws IOException {
        if (e instanceof IllegalArgumentException || e instanceof IOException) error(r, 400, e.getMessage());
        else if (e instanceof SecurityException) error(r, 403, e.getMessage());
        else if (e instanceof NoSuchElementException) error(r, 404, e.getMessage());
        else {
            log("Lỗi khi xử lý /api/orders", e);
            error(r, 500, "Không thể xử lý yêu cầu, vui lòng thử lại");
        }
    }

    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = path(q);
            if (p.equals("/")) ok(r, XuLyJson.stringify(service.danhSach(userId(q), roles(q),
                params(q, "status", "customerId", "salesRepId", "region", "from", "to", "page", "pageSize"))));
            else if (p.equals("/filters")) ok(r, XuLyJson.stringify(service.boLoc(userId(q), roles(q))));
            else ok(r, XuLyJson.stringify(service.chiTiet(parseId(p.substring(1)), userId(q), roles(q))));
        } catch (Exception e) {
            fail(r, e);
        }
    }

    protected void doPost(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String[] parts = path(q).split("/");
            if (parts.length != 3) throw new NoSuchElementException("Không tìm thấy chức năng");
            long id = parseId(parts[1]);
            Map<String, Object> in = XuLyJson.object(body(q));
            if (parts[2].equals("cancel")) ok(r, XuLyJson.stringify(service.huy(id, userId(q), roles(q), in.get("reason"))));
            else throw new NoSuchElementException("Không tìm thấy chức năng");
        } catch (Exception e) {
            fail(r, e);
        }
    }
}
