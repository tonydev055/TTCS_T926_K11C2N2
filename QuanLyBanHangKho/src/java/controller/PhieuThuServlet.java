package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.SQLException;
import java.util.*;
import service.PhieuThuService;
import util.XuLyJson;

/** S7-09: GET xem phiếu, POST tạo khoản thu. Không hỗ trợ PUT/DELETE. */
@WebServlet("/api/cash-receipts/*")
public final class PhieuThuServlet extends CoSoServlet {
    private final PhieuThuService service = new PhieuThuService();

    private static String path(HttpServletRequest q) {
        String p = q.getPathInfo();
        return p == null || p.isBlank() ? "/" : p;
    }
    private static long id(String raw) {
        if (raw == null || !raw.matches("[1-9][0-9]{0,17}"))
            throw new NoSuchElementException("Không tìm thấy phiếu thu");
        return Long.parseLong(raw);
    }
    private static long actor(HttpServletRequest q) {
        HttpSession s = q.getSession(false);
        Object value = s == null ? null : s.getAttribute("userId");
        if (!(value instanceof Number)) throw new SecurityException("Phiên đăng nhập không hợp lệ");
        return ((Number) value).longValue();
    }
    private void failure(HttpServletRequest q, HttpServletResponse r, Exception e) throws IOException {
        if (e instanceof NoSuchElementException) error(r, 404, e.getMessage());
        else if (e instanceof IllegalArgumentException) error(r, 400, e.getMessage());
        else if (e instanceof SecurityException) error(r, 403, e.getMessage());
        else {
            log("Cash receipt request failed", e);
            error(r, 500, "Không thể xử lý phiếu thu. Kiểm tra bảng cash_receipts đã được tạo chưa.");
        }
    }

    @Override protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = path(q);
            if (p.equals("/")) ok(r, XuLyJson.stringify(service.list()));
            else if (p.matches("/[1-9][0-9]{0,17}")) ok(r, XuLyJson.stringify(service.detail(id(p.substring(1)))));
            else error(r, 404, "Đường dẫn phiếu thu không tồn tại");
        } catch (Exception e) { failure(q, r, e); }
    }

    @Override protected void doPost(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            if (!path(q).equals("/")) { error(r, 404, "Đường dẫn phiếu thu không tồn tại"); return; }
            Map<String, Object> input = XuLyJson.object(body(q));
            Map<String, Object> created = service.create(input, actor(q));
            created(r, XuLyJson.stringify(created));
        } catch (Exception e) { failure(q, r, e); }
    }
}
