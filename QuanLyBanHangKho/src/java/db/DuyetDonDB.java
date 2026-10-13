package db;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import service.TrangThaiDonHang;
import util.KetNoiDB;
import util.XuLyJson;

/**
 * Duyệt đơn hàng (S4-05): danh sách đơn chờ duyệt kèm mức vi phạm hiện tại, và ghi quyết định
 * Duyệt / Từ chối / Trả lại sửa cùng lịch sử duyệt bất biến trong một giao dịch.
 */
public class DuyetDonDB extends CoSoDB {

    public static final Set<String> ACTIONS = Set.of("APPROVE", "REJECT", "RETURN");

    /** Đơn đang chờ duyệt, cũ nhất trước. Mỗi đơn có lý do lúc gửi (flags) và mức vi phạm tính lại theo số liệu hiện tại. */
    public List<Map<String, Object>> pending() throws SQLException {
        var rows = query(
            "SELECT o.id, o.code, o.source, o.payable, o.subtotal, o.discount_total AS discount, " +
            DonHangDB.iso("o.submitted_at") + " AS \"submittedAt\", o.approval_flags::text AS \"approvalFlagsJson\", " +
            "k.id AS \"customerId\", k.code AS \"customerCode\", k.name AS \"customerName\", k.region, " +
            "COALESCE(rep.full_name,'') AS \"salesRepName\", s.credit_limit AS \"creditLimit\", s.outstanding, " +
            "s.overdue_orders AS \"overdueOrders\", s.overdue_amount AS \"overdueAmount\", " +
            "(SELECT count(*) FROM order_lines l WHERE l.order_id=o.id) AS \"lineCount\" " +
            "FROM orders o JOIN customers k ON k.id=o.agent_id LEFT JOIN users rep ON rep.id=k.sales_rep_id " +
            "JOIN customer_credit_status s ON s.customer_id=k.id " +
            "WHERE o.status='PENDING_APPROVAL' ORDER BY o.submitted_at, o.id");
        if (rows.isEmpty()) return rows;
        List<Object> ids = new ArrayList<>();
        StringJoiner marks = new StringJoiner(",");
        for (var r : rows) { ids.add(r.get("id")); marks.add("?"); }
        Map<Object, List<Map<String, Object>>> floors = new HashMap<>();
        for (var l : query(
                "SELECT l.order_id, p.sku, p.name, u.name AS unit, round(l.base_price*l.conversion_factor,2) AS price, " +
                "round(l.floor_price*l.conversion_factor,2) AS floor, " +
                "round((l.floor_price-l.base_price)/NULLIF(l.floor_price,0)*100,1) AS pct " +
                "FROM order_lines l JOIN products p ON p.id=l.product_id JOIN product_units u ON u.id=l.unit_id " +
                "WHERE l.order_id IN (" + marks + ") AND l.price_overridden AND l.base_price < l.floor_price ORDER BY l.id",
                ids.toArray()))
            floors.computeIfAbsent(l.get("order_id"), k -> new ArrayList<>()).add(l);
        for (var r : rows) {
            r.put("approvalFlags", XuLyJson.parse(Objects.toString(r.remove("approvalFlagsJson"), "[]")));
            r.put("violations", violations(r, floors.getOrDefault(r.get("id"), List.of())));
        }
        return rows;
    }

    /** Mức vi phạm theo số liệu hiện tại: vượt hạn mức bao nhiêu, dòng nào thấp hơn giá sàn bao nhiêu %. */
    static List<Map<String, Object>> violations(Map<String, Object> r, List<Map<String, Object>> floorLines) {
        List<Map<String, Object>> out = new ArrayList<>();
        BigDecimal limit = (BigDecimal) r.get("creditLimit");
        BigDecimal outstanding = (BigDecimal) r.get("outstanding");
        BigDecimal payable = (BigDecimal) r.get("payable");
        BigDecimal over = outstanding.add(payable).subtract(limit);
        if (over.signum() > 0) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("code", "OVER_CREDIT");
            v.put("label", "Vượt hạn mức công nợ");
            v.put("amount", over);
            v.put("detail", "Vượt " + service.TaoDonHangService.tien(over) + ": công nợ hiện tại " +
                service.TaoDonHangService.tien(outstanding) + " + đơn " + service.TaoDonHangService.tien(payable) +
                ", hạn mức " + (limit.signum() == 0 ? "0 đ (không cho nợ)" : service.TaoDonHangService.tien(limit)));
            out.add(v);
        }
        for (var l : floorLines) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("code", "BELOW_FLOOR");
            v.put("label", "Bán dưới giá sàn");
            v.put("percent", l.get("pct"));
            v.put("detail", l.get("sku") + " — " + l.get("name") + ": " + service.TaoDonHangService.tien((BigDecimal) l.get("price")) +
                "/" + l.get("unit") + ", giá sàn " + service.TaoDonHangService.tien((BigDecimal) l.get("floor")) +
                " (thấp hơn " + ((BigDecimal) l.get("pct")).stripTrailingZeros().toPlainString().replace('.', ',') + "%)");
            out.add(v);
        }
        if (((Number) r.get("overdueOrders")).intValue() > 0) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("code", "OVERDUE");
            v.put("label", "Đại lý đang có nợ quá hạn");
            v.put("amount", r.get("overdueAmount"));
            v.put("detail", r.get("overdueOrders") + " đơn quá hạn, tổng " +
                service.TaoDonHangService.tien((BigDecimal) r.get("overdueAmount")));
            out.add(v);
        }
        return out;
    }

    /**
     * Ghi quyết định cho đơn đang Chờ duyệt. Duyệt thì giữ chỗ tồn tại kho phục vụ (thiếu tồn → báo lỗi, đơn giữ
     * nguyên Chờ duyệt). Trả về trạng thái mới của đơn.
     */
    public String decide(long id, String action, String comment, long actor) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                String reason = switch (action) {
                    case "APPROVE" -> "Quản lý duyệt" + (comment.isEmpty() ? "" : ": " + comment);
                    case "REJECT" -> "Quản lý từ chối: " + comment;
                    default -> "Quản lý trả lại để sửa: " + comment;
                };
                TrangThaiDonHang.setContext(c, actor, reason);
                var order = TruyVanDB.one(c,
                    "SELECT o.code, o.status, o.payable, o.approval_flags::text AS flags, o.agent_id FROM orders o " +
                    "WHERE o.id=? FOR UPDATE", id);
                if (order == null || order.get("agent_id") == null) throw new NoSuchElementException("Không tìm thấy đơn hàng");
                if (!TrangThaiDonHang.PENDING_APPROVAL.equals(order.get("status")))
                    throw new IllegalArgumentException("Đơn " + order.get("code") + " đang ở trạng thái " +
                        TrangThaiDonHang.label(order.get("status")) + ", không còn chờ duyệt");
                var credit = TruyVanDB.one(c, "SELECT outstanding, credit_limit FROM customer_credit_status WHERE customer_id=?",
                    order.get("agent_id"));
                var name = TruyVanDB.one(c, "SELECT full_name FROM users WHERE id=?", actor);
                TruyVanDB.update(c,
                    "INSERT INTO order_approvals(order_id,action,comment,flags,outstanding,credit_limit,payable,decided_by,decided_by_name) " +
                    "VALUES(?,?,?,?::jsonb,?,?,?,?,?)",
                    id, action, comment, order.get("flags"), credit == null ? null : credit.get("outstanding"),
                    credit == null ? null : credit.get("credit_limit"), order.get("payable"), actor,
                    name == null ? "Hệ thống" : name.get("full_name"));
                String status;
                if (action.equals("APPROVE")) {
                    // Kho phục vụ chốt khi gửi; đơn cũ chưa có thì lấy kho hiện tại của đại lý.
                    TruyVanDB.update(c, "UPDATE orders o SET warehouse_id=COALESCE(o.warehouse_id,k.warehouse_id) " +
                        "FROM customers k WHERE k.id=o.agent_id AND o.id=?", id);
                    GiuChoTonDB.reserve(c, id);
                    status = TrangThaiDonHang.APPROVED;
                } else status = action.equals("REJECT") ? TrangThaiDonHang.REJECTED : TrangThaiDonHang.DRAFT;
                TruyVanDB.update(c, "UPDATE orders SET status=? WHERE id=?", status, id);
                c.commit();
                return status;
            } catch (SQLException e) {
                c.rollback();
                throw TrangThaiDonHang.rethrow(e);
            } catch (RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Lịch sử duyệt của một đơn, cũ nhất trước. */
    public List<Map<String, Object>> history(long orderId) throws SQLException {
        return query("SELECT action, comment, payable, outstanding, credit_limit AS \"creditLimit\", " +
            "decided_by_name AS \"decidedByName\", " + DonHangDB.iso("decided_at") + " AS \"decidedAt\" " +
            "FROM order_approvals WHERE order_id=? ORDER BY decided_at, id", orderId);
    }
}
