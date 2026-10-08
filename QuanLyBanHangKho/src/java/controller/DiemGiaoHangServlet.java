package controller;

import db.DiemGiaoDB;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import service.ChinhSachChietKhauService;
import util.XuLyJson;

/**
 * Điểm giao hàng của đại lý. Cần quyền customers.assigned (nhân viên kinh doanh).
 *   GET    /api/delivery-points/customers?q=            tìm đại lý (kèm số điểm giao đang có)
 *   GET    /api/delivery-points?customerId=             danh sách điểm giao của một đại lý
 *   POST   /api/delivery-points                         thêm điểm giao
 *   PUT    /api/delivery-points/{id}                    sửa điểm giao
 *   POST   /api/delivery-points/{id}/default            đặt làm mặc định (body có customerId)
 *   DELETE /api/delivery-points/{id}?customerId=        xóa (ẩn) điểm giao
 */
@WebServlet("/api/delivery-points/*")
public class DiemGiaoHangServlet extends CoSoServlet {

    private final DiemGiaoDB dao = new DiemGiaoDB();

    private static long id(Object v) { return ChinhSachChietKhauService.positiveId(v); }

    private static String text(Map<String, Object> in, String key, String label, int max, boolean required) {
        String s = Objects.toString(in.get(key), "").trim();
        if (required && s.isEmpty()) throw new IllegalArgumentException(label + " không được để trống");
        if (s.length() > max) throw new IllegalArgumentException(label + " tối đa " + max + " ký tự");
        return s;
    }

    private static String phone(Map<String, Object> in) {
        String s = text(in, "phone", "Số điện thoại", 20, true);
        if (!s.matches("[0-9+()\\-. ]{8,20}")) throw new IllegalArgumentException("Số điện thoại không hợp lệ");
        return s;
    }

    private long agent(Object customerId) throws Exception {
        if (customerId == null) throw new IllegalArgumentException("Vui lòng chọn đại lý");
        long cid = id(customerId);
        if (!dao.isAgent(cid)) throw new NoSuchElementException("Không tìm thấy đại lý");
        return cid;
    }

    private void fail(HttpServletResponse r, Exception e) throws IOException {
        if (e instanceof IllegalArgumentException || e instanceof IOException) error(r, 400, e.getMessage());
        else if (e instanceof NoSuchElementException) error(r, 404, e.getMessage());
        else {
            java.util.logging.Logger.getLogger(DiemGiaoHangServlet.class.getName())
                .log(java.util.logging.Level.SEVERE, "Lỗi khi xử lý /api/delivery-points", e);
            error(r, 500, "Không thể xử lý yêu cầu, vui lòng thử lại");
        }
    }

    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            if ("/customers".equals(q.getPathInfo())) {
                ok(r, jsonRows(dao.customers(q.getParameter("q"))));
                return;
            }
            ok(r, jsonRows(dao.listByCustomer(agent(q.getParameter("customerId")))));
        } catch (Exception e) { fail(r, e); }
    }

    protected void doPost(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = Objects.toString(q.getPathInfo(), "/");
            Map<String, Object> in = XuLyJson.object(body(q));
            long cid = agent(in.get("customerId"));
            if (p.matches("/\\d+/default")) {
                dao.setDefault(Long.parseLong(p.substring(1, p.indexOf("/default"))), cid);
                ok(r, "{\"ok\":true}");
            } else if (p.equals("/") || p.isEmpty()) {
                long newId = dao.create(cid, text(in, "receiverName", "Người nhận", 150, true),
                    phone(in), text(in, "address", "Địa chỉ", 500, true),
                    text(in, "routeNote", "Ghi chú đường đi", 1000, false), Boolean.TRUE.equals(in.get("isDefault")));
                created(r, XuLyJson.stringify(Map.of("id", newId)));
            } else throw new NoSuchElementException("Không tìm thấy chức năng");
        } catch (Exception e) { fail(r, e); }
    }

    protected void doPut(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = Objects.toString(q.getPathInfo(), "/");
            if (!p.matches("/\\d+")) throw new IllegalArgumentException("Thiếu mã điểm giao hàng");
            Map<String, Object> in = XuLyJson.object(body(q));
            long cid = agent(in.get("customerId"));
            dao.update(Long.parseLong(p.substring(1)), cid, text(in, "receiverName", "Người nhận", 150, true),
                phone(in), text(in, "address", "Địa chỉ", 500, true),
                text(in, "routeNote", "Ghi chú đường đi", 1000, false), Boolean.TRUE.equals(in.get("isDefault")));
            ok(r, "{\"ok\":true}");
        } catch (Exception e) { fail(r, e); }
    }

    protected void doDelete(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = Objects.toString(q.getPathInfo(), "/");
            if (!p.matches("/\\d+")) throw new IllegalArgumentException("Thiếu mã điểm giao hàng");
            dao.remove(Long.parseLong(p.substring(1)), agent(q.getParameter("customerId")));
            ok(r, "{\"ok\":true}");
        } catch (Exception e) { fail(r, e); }
    }
}
