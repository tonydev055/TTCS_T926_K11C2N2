package service;

import db.DonHangDB;
import java.sql.SQLException;
import java.util.*;
import security.PhanQuyen;

/**
 * Nghiệp vụ đơn hàng đã lập (S4-06): xem danh sách, chi tiết, vòng đời và huỷ đơn.
 * Phạm vi bản ghi: có quyền orders.read thì xem mọi đơn; chỉ có orders.own thì chỉ xem đơn của đại lý mình phụ trách.
 */
public class DonHangService {

    private final DonHangDB db = new DonHangDB();

    /** Quote a line in base SKU units. Caller supplies the authoritative price-list price. */
    public Map<String, Object> tinhGiaDong(long productId, int quantity, java.math.BigDecimal price) throws SQLException {
        return new db.ChinhSachChietKhauDB().quote(Map.of("productId", productId, "quantity", quantity, "unitPrice", price));
    }

    /** null = xem toàn bộ; ngược lại là id nhân viên phụ trách để giới hạn phạm vi. */
    public static Long scope(long userId, Collection<String> roles) {
        if (PhanQuyen.allows(roles, "orders.read")) return null;
        if (PhanQuyen.allows(roles, "orders.own")) return userId;
        throw new SecurityException("Bạn không có quyền xem đơn hàng");
    }

    /** Huỷ được: quản lý duyệt đơn (orders.approve) với mọi đơn, nhân viên kinh doanh với đơn của đại lý mình phụ trách. */
    static Long cancelScope(long userId, Collection<String> roles) {
        if (PhanQuyen.allows(roles, "orders.approve")) return null;
        if (PhanQuyen.allows(roles, "orders.own")) return userId;
        throw new SecurityException("Bạn không có quyền huỷ đơn hàng");
    }

    public Map<String, Object> danhSach(long userId, Collection<String> roles, Map<String, String> filter) throws SQLException {
        String status = blank(filter.get("status"));
        if (status != null && !TrangThaiDonHang.known(status)) throw new IllegalArgumentException("Trạng thái không hợp lệ");
        java.time.LocalDate from = date(filter.get("from"), "Từ ngày"), to = date(filter.get("to"), "Đến ngày");
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("Từ ngày phải trước hoặc bằng Đến ngày");
        String region = blank(filter.get("region"));
        if (region != null && region.length() > 150) throw new IllegalArgumentException("Khu vực không hợp lệ");
        var f = new DonHangDB.Filter(status, id(filter.get("customerId")), id(filter.get("salesRepId")), region, from, to);
        int page = intParam(filter.get("page"), 1, 1, 100_000);
        int size = intParam(filter.get("pageSize"), 20, 1, 100);
        Map<String, Object> out = db.list(scope(userId, roles), userId, f, page, size);
        out.put("statuses", TrangThaiDonHang.labels());
        return out;
    }

    public Map<String, Object> boLoc(long userId, Collection<String> roles) throws SQLException {
        return db.filterOptions(scope(userId, roles));
    }

    static java.time.LocalDate date(String raw, String label) {
        String s = blank(raw);
        if (s == null) return null;
        try { return java.time.LocalDate.parse(s); }
        catch (java.time.format.DateTimeParseException e) { throw new IllegalArgumentException(label + " không hợp lệ"); }
    }

    static Long id(String raw) {
        String s = blank(raw);
        if (s == null) return null;
        if (!s.matches("[1-9][0-9]{0,17}")) throw new IllegalArgumentException("Mã lọc không hợp lệ");
        return Long.parseLong(s);
    }

    public Map<String, Object> chiTiet(long id, long userId, Collection<String> roles) throws SQLException {
        Map<String, Object> order = db.detail(id, scope(userId, roles), userId);
        if (order == null) throw new NoSuchElementException("Không tìm thấy đơn hàng");
        order.put("actions", actions(order, userId, roles));
        return order;
    }

    /** Các thao tác người dùng hiện tại được phép làm trên đơn; máy chủ vẫn kiểm tra lại khi thực hiện. */
    Map<String, Object> actions(Map<String, Object> order, long userId, Collection<String> roles) {
        Map<String, Object> a = new LinkedHashMap<>();
        boolean ownCustomer = Objects.equals(order.get("salesRepId"), userId);
        boolean manager = PhanQuyen.allows(roles, "orders.approve");
        boolean rep = PhanQuyen.allows(roles, "orders.own") && ownCustomer;
        a.put("canCancel", TrangThaiDonHang.CANCELLABLE.contains(String.valueOf(order.get("status"))) && (manager || rep));
        a.put("canApprove", TrangThaiDonHang.PENDING_APPROVAL.equals(order.get("status")) && manager);
        return a;
    }

    public Map<String, Object> huy(long id, long userId, Collection<String> roles, Object rawReason) throws SQLException {
        String reason = Objects.toString(rawReason, "").trim();
        if (reason.isEmpty()) throw new IllegalArgumentException("Vui lòng nhập lý do huỷ đơn");
        if (reason.length() > 500) throw new IllegalArgumentException("Lý do huỷ tối đa 500 ký tự");
        db.cancel(id, cancelScope(userId, roles), userId, reason);
        return chiTiet(id, userId, roles);
    }

    static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static int intParam(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int v = Integer.parseInt(raw.trim());
            if (v < min || v > max) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Tham số phân trang không hợp lệ");
        }
    }
}
