package controller;

import db.NguoiDungDB;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import service.ThuDienTuService;
import util.*;

@WebServlet("/api/users/*")
public class NguoiDungServlet extends CoSoServlet {

    private final NguoiDungDB db = new NguoiDungDB();

    protected void doDelete(HttpServletRequest q, HttpServletResponse r) throws IOException {
        String path = q.getPathInfo();
        if (path == null || !path.matches("/\\d+")) {
            error(r, 400, "Đường dẫn tài khoản không hợp lệ");
            return;
        }
        try {
            long id = Long.parseLong(path.substring(1)),
                actor = ((Number) q.getSession().getAttribute("userId")).longValue();
            if (!db.deleteUser(id, actor)) {
                error(r, 404, "Không tìm thấy tài khoản");
                return;
            }
            ok(r, "{\"message\":\"Đã xóa tài khoản\"}");
        } catch (IllegalArgumentException e) {
            error(r, 400, e.getMessage());
        } catch (java.sql.SQLException e) {
            if ("23503".equals(e.getSQLState())) error(
                r,
                409,
                "Tài khoản đã có dữ liệu liên quan hoặc nhật ký. Hãy khóa tài khoản thay vì xóa"
            );
            else error(r, 500, "Không thể xóa tài khoản. Vui lòng thử lại");
        }
    }

    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String p = q.getPathInfo();
            if (p != null && !p.equals("/")) {
                var row = db.findById(Long.parseLong(p.substring(1)));
                if (row == null) {
                    error(r, 404, "Không tìm thấy tài khoản");
                    return;
                }
                ok(r, jsonRows(List.of(row)));
                return;
            }
            int page = Math.max(1, intParam(q, "page", 1)),
                size = Math.min(100, Math.max(1, intParam(q, "size", 20)));
            String search = q.getParameter("search"),
                role = q.getParameter("role"),
                status = q.getParameter("status");
            var rows = db.search(search, role, status, page, size);
            String stats = jsonRows(List.of(db.stats()));
            stats = stats.substring(1, stats.length() - 1);
            ok(
                r,
                "{\"items\":" +
                    jsonRows(rows) +
                    ",\"page\":" +
                    page +
                    ",\"size\":" +
                    size +
                    ",\"total\":" +
                    db.count(search, role, status) +
                    ",\"stats\":" +
                    stats +
                    "}"
            );
        } catch (Exception e) {
            error(r, 400, e.getMessage());
        }
    }

    protected void doPost(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String b = body(q),
                path = q.getPathInfo();
            if (path != null && path.matches("/\\d+/(lock|unlock)")) {
                String[] x = path.split("/");
                long id = Long.parseLong(x[1]);
                if (
                    id == ((Number) q.getSession().getAttribute("userId")).longValue() &&
                    "lock".equals(x[2])
                ) throw new IllegalArgumentException("Không thể tự khoá tài khoản của chính mình");
                db.setLocked(id, "lock".equals(x[2]), YeuCauJson.text(b, "reason"));
                ok(r, "{\"message\":\"Đã cập nhật trạng thái tài khoản\"}");
                return;
            }
            String password = MatKhauUtil.DEFAULT_USER_PASSWORD,
                email = YeuCauJson.text(b, "email");
            long id = db.create(
                YeuCauJson.text(b, "username"),
                email,
                YeuCauJson.text(b, "fullName"),
                YeuCauJson.text(b, "phone"),
                MatKhauUtil.encode(password),
                YeuCauJson.text(b, "territory"),
                YeuCauJson.strings(b, "roles"),
                YeuCauJson.strings(b, "warehouses")
            );
            ThuDienTuService.activation(email, password);
            created(
                r,
                "{\"id\":" + id + ",\"message\":\"Đã tạo tài khoản và gửi thông tin kích hoạt\"}"
            );
        } catch (Exception e) {
            error(
                r,
                e.getMessage() != null && e.getMessage().contains("duplicate key") ? 409 : 400,
                e.getMessage()
            );
        }
    }

    protected void doPut(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            long id = Long.parseLong(q.getPathInfo().substring(1));
            String b = body(q);
            long actor = ((Number) q.getSession().getAttribute("userId")).longValue();
            db.updateUser(
                id,
                YeuCauJson.text(b, "fullName"),
                YeuCauJson.text(b, "phone"),
                YeuCauJson.text(b, "territory"),
                YeuCauJson.strings(b, "roles"),
                YeuCauJson.strings(b, "warehouses"),
                actor
            );
            ok(r, "{\"message\":\"Đã cập nhật tài khoản\"}");
        } catch (Exception e) {
            error(r, 400, e.getMessage());
        }
    }

    private int intParam(HttpServletRequest q, String name, int fallback) {
        try {
            return Integer.parseInt(q.getParameter(name));
        } catch (Exception e) {
            return fallback;
        }
    }
}
