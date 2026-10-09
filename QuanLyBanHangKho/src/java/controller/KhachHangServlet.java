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
 * GET /api/customers/?search&region&groupId&salesRepId&status&page&size — danh sách theo phạm vi (S3-08).
 * search khớp mã, tên (không phân biệt dấu), mã số thuế, số điện thoại (bỏ qua dấu cách, +84 ≡ 0);
 * status nhận ACTIVE | INACTIVE | LOCKED (đang bị khoá giao dịch).
 * GET /api/customers/masters — thêm seesAll: true khi được xem mọi đại lý (hiện bộ lọc người phụ trách).
 * GET /api/customers/masters — nhóm khách hàng, nhân viên kinh doanh, khu vực.
 * GET /api/customers/{id} — chi tiết kèm bảng giá đang áp dụng.
 * POST /api/customers/, PUT /api/customers/{id}, DELETE /api/customers/{id} — cần customers.write.
 * GET|POST /api/customers/{id}/addresses, PUT|DELETE /api/customers/{id}/addresses/{addressId},
 * POST /api/customers/{id}/addresses/{addressId}/default — điểm giao hàng (S3-04).
 * PUT /api/customers/{id}/credit {credit_limit, credit_days, reason}, GET /api/customers/{id}/credit-history
 * — hạn mức công nợ (S3-05), cần customers.credit để sửa.
 * PUT /api/customers/{id}/assignment {sales_rep_id, reason}, GET /api/customers/{id}/assignment-history,
 * GET /api/customers/reps, POST /api/customers/transfer {from_user_id, to_user_id, reason, customer_ids?}
 * — phân công và chuyển giao đại lý (S3-06), cần customers.assign để ghi.
 * POST /api/customers/{id}/lock {reason}, POST /api/customers/{id}/unlock {reason?},
 * GET /api/customers/{id}/lock-history — khoá/mở giao dịch (S3-07), cần customers.lock để ghi.
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

    private static long addressId(String raw) {
        if (!raw.matches("[1-9][0-9]{0,17}")) throw new NoSuchElementException("Không tìm thấy điểm giao hàng");
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
            else if (p.length == 1 && p[0].equals("reps")) ok(r, XuLyJson.stringify(service.repWorkload(user(q))));
            else if (p.length == 1) ok(r, XuLyJson.stringify(service.detail(user(q), id(p[0]))));
            else if (p.length == 2 && p[1].equals("addresses"))
                ok(r, XuLyJson.stringify(service.addresses(user(q), id(p[0]))));
            else if (p.length == 2 && p[1].equals("assignment-history"))
                ok(r, XuLyJson.stringify(service.assignmentHistory(user(q), id(p[0]))));
            else if (p.length == 2 && p[1].equals("lock-history"))
                ok(r, XuLyJson.stringify(service.lockHistory(user(q), id(p[0]))));
            else if (p.length == 2 && p[1].equals("credit-history"))
                ok(r, XuLyJson.stringify(service.creditHistory(user(q), id(p[0]))));
            else throw new NoSuchElementException("Endpoint không tồn tại");
        } catch (Exception e) {
            failure(r, e);
        }
    }

    protected void doPost(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String[] p = parts(q);
            String raw = body(q);
            Map<String, Object> input = raw.isBlank() ? new HashMap<>() : XuLyJson.object(raw);
            if (p.length == 0) {
                long id = service.create(user(q), input);
                created(r, XuLyJson.stringify(Map.of("id", id, "message", "Đã lưu hồ sơ đại lý")));
            } else if (p.length == 2 && (p[1].equals("lock") || p[1].equals("unlock"))) {
                ok(r, XuLyJson.stringify(service.setTradingLock(user(q), id(p[0]), p[1].equals("lock"), input)));
            } else if (p.length == 1 && p[0].equals("transfer")) {
                ok(r, XuLyJson.stringify(service.transfer(user(q), input)));
            } else if (p.length == 2 && p[1].equals("addresses")) {
                long id = service.createAddress(user(q), id(p[0]), input);
                created(r, XuLyJson.stringify(Map.of("id", id, "message", "Đã lưu điểm giao hàng")));
            } else if (p.length == 4 && p[1].equals("addresses") && p[3].equals("default")) {
                service.setDefaultAddress(user(q), id(p[0]), addressId(p[2]));
                ok(r, "{\"message\":\"Đã đặt điểm giao mặc định\"}");
            } else throw new NoSuchElementException("Endpoint không tồn tại");
        } catch (Exception e) {
            failure(r, e);
        }
    }

    protected void doPut(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String[] p = parts(q);
            if (p.length == 2 && p[1].equals("assignment")) {
                service.assign(user(q), id(p[0]), XuLyJson.object(body(q)));
                ok(r, "{\"message\":\"Đã cập nhật người phụ trách\"}");
                return;
            }
            if (p.length == 2 && p[1].equals("credit")) {
                service.updateCredit(user(q), id(p[0]), XuLyJson.object(body(q)));
                ok(r, "{\"message\":\"Đã cập nhật hạn mức công nợ\"}");
                return;
            }
            if (p.length == 3 && p[1].equals("addresses")) {
                service.updateAddress(user(q), id(p[0]), addressId(p[2]), XuLyJson.object(body(q)));
                ok(r, "{\"message\":\"Đã lưu điểm giao hàng\"}");
                return;
            }
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
            if (p.length == 3 && p[1].equals("addresses")) {
                service.deleteAddress(user(q), id(p[0]), addressId(p[2]));
                ok(r, "{\"deleted\":true,\"message\":\"Đã gỡ điểm giao hàng\"}");
                return;
            }
            if (p.length != 1) throw new NoSuchElementException("Endpoint không tồn tại");
            service.delete(user(q), id(p[0]));
            ok(r, "{\"deleted\":true,\"message\":\"Đã xoá đại lý\"}");
        } catch (Exception e) {
            failure(r, e);
        }
    }
}
