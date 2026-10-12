package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.SQLException;
import java.util.*;
import service.KhachHangService;
import service.KhachHangService.NguoiThaoTac;
import util.XuLyJson;

/**
 * API hồ sơ đại lý.
 * GET /api/customers/?search&region&groupId&salesRepId&status&page&size — danh sách theo phạm vi.
 * GET /api/customers/masters — nhóm khách hàng, nhân viên kinh doanh, khu vực.
 * GET /api/customers/{id} — chi tiết kèm bảng giá đang áp dụng.
 * POST /api/customers/, PUT /api/customers/{id}, DELETE /api/customers/{id} — cần customers.write.
 */
@WebServlet("/api/customers/*")
public class KhachHangServlet extends CoSoServlet {

    private final KhachHangService service = new KhachHangService();

    @SuppressWarnings("unchecked")
    private NguoiThaoTac user(HttpServletRequest q) {
        HttpSession s = q.getSession(false);
        return new NguoiThaoTac(
            ((Number) s.getAttribute("userId")).longValue(),
            (List<String>) s.getAttribute("roles")
        );
    }

    private static String[] parts(HttpServletRequest q) {
        String path = Objects.toString(q.getPathInfo(), "/");
        return Arrays.stream(path.split("/")).filter(x -> !x.isEmpty()).toArray(String[]::new);
    }

    private static long id(String raw) {
        if (!raw.matches("[1-9][0-9]{0,17}")) throw new NoSuchElementException("Không tìm thấy đại lý");
        return Long.parseLong(raw);
    }

    private void failure(HttpServletResponse r, Exception e) throws IOException {
        if (e instanceof NoSuchElementException) error(r, 404, e.getMessage());
        else if (e instanceof SecurityException) error(r, 403, e.getMessage());
        else if (e instanceof IllegalArgumentException) error(r, 400, e.getMessage());
        else if (e instanceof SQLException sql && "23505".equals(sql.getSQLState()))
            error(r, 409, "Mã đại lý đã tồn tại. Vui lòng dùng mã khác");
        else if (e instanceof SQLException sql && "23503".equals(sql.getSQLState()))
            error(r, 400, "Dữ liệu tham chiếu không tồn tại hoặc đại lý đã được sử dụng");
        else if (e instanceof SQLException sql && "23514".equals(sql.getSQLState()))
            error(r, 400, "Dữ liệu đại lý không đáp ứng điều kiện hợp lệ");
        else {
            log("Customer request failed", e);
            error(r, 500, "Không thể xử lý hồ sơ đại lý");
        }
    }

    private static Map<String, String> query(HttpServletRequest q) {
        Map<String, String> out = new HashMap<>();
        q.getParameterMap().forEach((k, v) -> out.put(k, v.length == 0 ? "" : v[0]));
        return out;
    }

    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String[] p = parts(q);
            if (p.length == 0) ok(r, XuLyJson.stringify(service.list(user(q), query(q))));
            else if (p.length == 1 && p[0].equals("masters")) ok(r, XuLyJson.stringify(service.masters(user(q))));
            else if (p.length == 1) ok(r, XuLyJson.stringify(service.detail(user(q), id(p[0]))));
            else throw new NoSuchElementException("Endpoint không tồn tại");
        } catch (Exception e) {
            failure(r, e);
        }
    }

    protected void doPost(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String[] p = parts(q);
            var input = XuLyJson.object(body(q));
            if (p.length == 0) {
                long id = service.create(user(q), input);
                created(r, XuLyJson.stringify(Map.of("id", id, "message", "Đã lưu hồ sơ đại lý")));
            } else throw new NoSuchElementException("Endpoint không tồn tại");
        } catch (Exception e) {
            failure(r, e);
        }
    }

    protected void doPut(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String[] p = parts(q);
            if (p.length != 1) throw new NoSuchElementException("Endpoint không tồn tại");
            long id = id(p[0]);
            service.update(user(q), id, XuLyJson.object(body(q)));
            ok(r, XuLyJson.stringify(Map.of("id", id, "message", "Đã lưu hồ sơ đại lý")));
        } catch (Exception e) {
            failure(r, e);
        }
    }

    protected void doDelete(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String[] p = parts(q);
            if (p.length != 1) throw new NoSuchElementException("Endpoint không tồn tại");
            service.delete(user(q), id(p[0]));
            ok(r, "{\"deleted\":true,\"message\":\"Đã xoá đại lý\"}");
        } catch (Exception e) {
            failure(r, e);
        }
    }
}
