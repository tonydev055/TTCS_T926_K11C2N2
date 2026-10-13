package db;

import java.sql.*;
import java.util.*;
import service.TrangThaiDonHang;
import util.KetNoiDB;

/**
 * Truy vấn đơn hàng đã lập (S4-06): danh sách, chi tiết, lịch sử trạng thái và huỷ đơn.
 * repId != null giới hạn phạm vi vào các đại lý do nhân viên đó phụ trách.
 */
public class DonHangDB extends CoSoDB {

    static final String ISO = "'YYYY-MM-DD\"T\"HH24:MI:SS'";

    static String iso(String column) {
        return "to_char(" + column + "," + ISO + ")";
    }

    /** Phần FROM dùng chung: đại lý của đơn, nhân viên phụ trách và người tạo. */
    static final String FROM =
        " FROM orders o LEFT JOIN customers k ON k.id=o.agent_id" +
        " LEFT JOIN users rep ON rep.id=k.sales_rep_id" +
        " LEFT JOIN users cb ON cb.id=o.created_by" +
        " LEFT JOIN users cu ON cu.id=o.customer_id ";

    static final String HEADER =
        "SELECT o.id, o.code, o.status, o.source, o.agent_id AS \"customerId\", COALESCE(k.code,'') AS \"customerCode\", " +
        "COALESCE(k.name, cu.full_name, '') AS \"customerName\", COALESCE(k.phone,'') AS \"customerPhone\", " +
        "COALESCE(k.tax_code,'') AS \"customerTaxCode\", COALESCE(k.region,'') AS region, " +
        "k.sales_rep_id AS \"salesRepId\", COALESCE(rep.full_name,'') AS \"salesRepName\", " +
        "COALESCE(cb.full_name,'') AS \"createdByName\", o.delivery_address AS \"deliveryAddress\", " +
        "o.desired_delivery_date::text AS \"desiredDate\", o.note, o.subtotal, o.discount_total AS discount, o.payable, " +
        "COALESCE(o.cancel_reason,'') AS \"cancelReason\", o.approval_flags::text AS \"approvalFlagsJson\", " +
        iso("o.created_at") + " AS \"createdAt\", " + iso("o.updated_at") + " AS \"updatedAt\", " +
        iso("o.submitted_at") + " AS \"submittedAt\", " + iso("o.approved_at") + " AS \"approvedAt\", " +
        iso("o.shipped_at") + " AS \"shippedAt\", " + iso("o.delivered_at") + " AS \"deliveredAt\", " +
        iso("o.closed_at") + " AS \"closedAt\", " + iso("o.cancelled_at") + " AS \"cancelledAt\"";

    static void label(Map<String, Object> row) {
        row.put("statusLabel", TrangThaiDonHang.label(row.get("status")));
        row.put("approvalFlags", util.XuLyJson.parse(Objects.toString(row.remove("approvalFlagsJson"), "[]")));
    }

    /** Danh sách đơn đã lập, mới nhất trước. Nháp chỉ hiện với người tạo (nằm ở màn hình tạo đơn). */
    public Map<String, Object> list(Long repId, long userId, String status, int page, int size) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE (o.status<>'DRAFT' OR o.created_by=?)");
        List<Object> args = new ArrayList<>(List.of(userId));
        if (repId != null) { where.append(" AND k.sales_rep_id=?"); args.add(repId); }
        if (status != null) { where.append(" AND o.status=?"); args.add(status); }
        var total = query("SELECT count(*) AS n, COALESCE(sum(o.payable),0) AS amount" + FROM + where, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        var rows = query(HEADER + ", (SELECT count(*) FROM order_lines l WHERE l.order_id=o.id) AS \"lineCount\"" +
            FROM + where + " ORDER BY o.created_at DESC, o.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        rows.forEach(DonHangDB::label);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", rows);
        out.put("total", total.get(0).get("n"));
        out.put("totalAmount", total.get(0).get("amount"));
        out.put("page", page);
        out.put("pageSize", size);
        return out;
    }

    /** Chi tiết đơn kèm dòng hàng và lịch sử trạng thái; null nếu không có hoặc ngoài phạm vi. */
    public Map<String, Object> detail(long id, Long repId, long userId) throws SQLException {
        var rows = repId == null
            ? query(HEADER + FROM + " WHERE o.id=? AND (o.status<>'DRAFT' OR o.created_by=?)", id, userId)
            : query(HEADER + FROM + " WHERE o.id=? AND (o.status<>'DRAFT' OR o.created_by=?) AND k.sales_rep_id=?", id, userId, repId);
        if (rows.isEmpty()) return null;
        Map<String, Object> order = new LinkedHashMap<>(rows.get(0));
        label(order);
        order.put("lines", lines(id));
        order.put("history", history(id));
        order.put("approvals", new DuyetDonDB().history(id));
        return order;
    }

    public List<Map<String, Object>> lines(long orderId) throws SQLException {
        return query(
            "SELECT l.id, l.product_id AS \"productId\", p.sku, p.name, u.name AS \"unitName\", " +
            "trim_scale(l.quantity) AS quantity, trim_scale(l.conversion_factor) AS \"conversionFactor\", " +
            "trim_scale(l.base_quantity) AS \"baseQuantity\", p.base_unit AS \"baseUnit\", l.base_price AS \"basePrice\", " +
            "round(l.base_price*l.conversion_factor,2) AS \"unitPrice\", (l.line_total + l.discount_amount) AS subtotal, " +
            "l.discount_amount AS discount, l.line_total AS payable, l.price_overridden AS \"priceOverridden\", " +
            "round(l.list_price*l.conversion_factor,2) AS \"listPrice\", round(l.floor_price*l.conversion_factor,2) AS \"floorPrice\" " +
            "FROM order_lines l JOIN products p ON p.id=l.product_id JOIN product_units u ON u.id=l.unit_id " +
            "WHERE l.order_id=? ORDER BY l.id", orderId);
    }

    public List<Map<String, Object>> history(long orderId) throws SQLException {
        var rows = query(
            "SELECT from_status AS \"fromStatus\", to_status AS \"toStatus\", reason, changed_by_name AS \"changedByName\", " +
            iso("changed_at") + " AS \"changedAt\" FROM order_status_history WHERE order_id=? ORDER BY changed_at, id", orderId);
        for (var r : rows) {
            r.put("fromLabel", r.get("fromStatus") == null ? "" : TrangThaiDonHang.label(r.get("fromStatus")));
            r.put("toLabel", TrangThaiDonHang.label(r.get("toStatus")));
        }
        return rows;
    }

    /**
     * Huỷ đơn trong một giao dịch: khoá đơn, kiểm tra phạm vi, đổi trạng thái (trigger kiểm tra bước chuyển,
     * ghi lịch sử và mốc thời gian), rồi nhả các phần đã giữ cho đơn.
     */
    public void cancel(long id, Long repId, long actor, String reason) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                TrangThaiDonHang.setContext(c, actor, reason);
                var row = TruyVanDB.one(c,
                    "SELECT o.status, o.code, o.created_by, k.sales_rep_id FROM orders o LEFT JOIN customers k ON k.id=o.agent_id " +
                    "WHERE o.id=? FOR UPDATE OF o", id);
                if (row == null || (repId != null && !Objects.equals(row.get("sales_rep_id"), repId))
                    || (TrangThaiDonHang.DRAFT.equals(row.get("status")) && !Objects.equals(row.get("created_by"), actor)))
                    throw new NoSuchElementException("Không tìm thấy đơn hàng");
                if (!TrangThaiDonHang.CANCELLABLE.contains(String.valueOf(row.get("status"))))
                    throw new IllegalArgumentException(
                        TrangThaiDonHang.FINISHED.contains(String.valueOf(row.get("status")))
                            ? "Đơn " + row.get("code") + " đã " + TrangThaiDonHang.label(row.get("status")).toLowerCase() + ", không huỷ được nữa"
                            : "Đơn " + row.get("code") + " đã xuất kho, không huỷ được. Hãy lập phiếu trả hàng");
                TruyVanDB.update(c, "UPDATE orders SET status='CANCELLED', cancel_reason=? WHERE id=?", reason, id);
                afterCancel(c, id);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw TrangThaiDonHang.rethrow(e);
            } catch (RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** S4-03: huỷ đơn thì nhả tồn đang giữ chỗ cho đơn trong cùng giao dịch. */
    protected void afterCancel(Connection c, long orderId) throws SQLException {
        GiuChoTonDB.release(c, orderId);
    }
}
