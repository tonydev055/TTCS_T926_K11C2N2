package db;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import service.TrangThaiDonHang;
import util.KetNoiDB;

/**
 * Cổng đặt hàng của đại lý (S4-10). Mọi truy vấn giới hạn theo đại lý gắn với tài khoản đang đăng nhập
 * (customers.portal_user_id); không nhận mã đại lý từ phía giao diện.
 */
public class CongDaiLyDB extends CoSoDB {
    private static final Set<String> TRIP_STATES = Set.of("SHIPPED", "DELIVERED", "CLOSED");

    /** Đại lý gắn với tài khoản; null nếu tài khoản chưa được liên kết. */
    public Map<String, Object> customerOf(long userId) throws SQLException {
        var rows = query(
            "SELECT k.id, k.code, k.name, k.phone, k.region, k.status, k.trading_locked AS \"tradingLocked\", " +
            "COALESCE(k.lock_reason,'') AS \"lockReason\", k.sales_rep_id AS \"salesRepId\", " +
            "COALESCE(u.full_name,'') AS \"salesRepName\", COALESCE(u.phone,'') AS \"salesRepPhone\", " +
            "g.name AS \"groupName\" FROM customers k JOIN customer_groups g ON g.id=k.customer_group_id " +
            "LEFT JOIN users u ON u.id=k.sales_rep_id WHERE k.portal_user_id=?", userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> deliveryPoints(long customerId) throws SQLException {
        return query("SELECT id, address, recipient_name AS \"receiverName\", phone, is_default AS \"isDefault\" " +
            "FROM customer_addresses WHERE customer_id=? AND active ORDER BY is_default DESC, id", customerId);
    }

    public Map<String, Object> priceList(long priceListId) throws SQLException {
        var rows = query("SELECT name, valid_from::text AS \"validFrom\", valid_to::text AS \"validTo\" FROM price_lists WHERE id=?",
            priceListId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Tạo đơn đại lý tự đặt ở trạng thái Chờ nhân viên xác nhận, trong một giao dịch. Trả về id đơn. */
    public long createOrder(long userId, long customerId, long priceListId, long pointId, String address, LocalDate date,
                            String note, BigDecimal subtotal, BigDecimal discount, BigDecimal payable,
                            List<Map<String, Object>> lines) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                TrangThaiDonHang.setContext(c, userId, "Đại lý gửi đơn qua cổng đặt hàng");
                // Khoá hồ sơ đại lý: liên kết tài khoản và điểm giao phải còn đúng tại thời điểm ghi.
                var customer = TruyVanDB.one(c, "SELECT id FROM customers WHERE id=? AND portal_user_id=? FOR UPDATE", customerId, userId);
                if (customer == null) throw new NoSuchElementException("Tài khoản chưa được liên kết với đại lý");
                if (TruyVanDB.one(c, "SELECT id FROM customer_addresses WHERE id=? AND customer_id=? AND active FOR SHARE",
                        pointId, customerId) == null)
                    throw new IllegalArgumentException("Điểm giao hàng không thuộc đại lý hoặc đã ngừng sử dụng");
                long id;
                try (PreparedStatement p = c.prepareStatement("SELECT nextval(pg_get_serial_sequence('orders','id'))");
                     ResultSet r = p.executeQuery()) {
                    r.next();
                    id = r.getLong(1);
                }
                TruyVanDB.update(c,
                    "INSERT INTO orders(id,code,agent_id,price_list_id,status,source,created_by,delivery_address,delivery_address_id," +
                    "desired_delivery_date,note,subtotal,discount_total,payable) VALUES(?,?,?,?,'PENDING_CONFIRM','PORTAL',?,?,?,?,?,?,?,?)",
                    id, String.format("DH%06d", id), customerId, priceListId, userId, address, pointId,
                    java.sql.Date.valueOf(date), note, subtotal, discount, payable);
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO order_lines(order_id,product_id,unit_id,quantity,conversion_factor,base_price,discount_amount," +
                        "line_total,policy_id,list_price,floor_price,price_overridden) VALUES(?,?,?,?,?,?,?,?,?,?,?,FALSE)")) {
                    for (var l : lines) {
                        p.setLong(1, id);
                        p.setLong(2, ((Number) l.get("productId")).longValue());
                        p.setLong(3, ((Number) l.get("unitId")).longValue());
                        p.setBigDecimal(4, (BigDecimal) l.get("quantity"));
                        p.setBigDecimal(5, (BigDecimal) l.get("conversionFactor"));
                        p.setBigDecimal(6, (BigDecimal) l.get("basePrice"));
                        p.setBigDecimal(7, (BigDecimal) l.get("discount"));
                        p.setBigDecimal(8, (BigDecimal) l.get("payable"));
                        p.setObject(9, l.get("policyId"), Types.BIGINT);
                        p.setBigDecimal(10, (BigDecimal) l.get("listBasePrice"));
                        p.setBigDecimal(11, (BigDecimal) l.get("floorBasePrice"));
                        p.addBatch();
                    }
                    p.executeBatch();
                }
                c.commit();
                return id;
            } catch (SQLException e) {
                c.rollback();
                throw TrangThaiDonHang.rethrow(e);
            } catch (RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Đơn của đại lý (không gồm nháp nội bộ của nhân viên), mới nhất trước. */
    public List<Map<String, Object>> orders(long customerId) throws SQLException {
        var rows = query(
            "SELECT o.id, o.code, o.status, o.source, o.payable, " + DonHangDB.iso("o.created_at") + " AS \"createdAt\", " +
            "o.delivery_address AS \"deliveryAddress\", o.desired_delivery_date::text AS \"desiredDate\", (SELECT count(*) FROM order_lines l WHERE l.order_id=o.id) AS \"lineCount\" " +
            "FROM orders o WHERE o.agent_id=? AND o.status<>'DRAFT' ORDER BY o.created_at DESC, o.id DESC LIMIT 100", customerId);
        for (var r : rows) {
            r.put("statusLabel", TrangThaiDonHang.label(r.get("status")));
            if (TRIP_STATES.contains(String.valueOf(r.get("status")))) r.put("trip", trip(((Number) r.get("id")).longValue()));
        }
        return rows;
    }

    /**
     * S6-09: chuyến giao đã khởi hành của đơn (mốc "Đang giao"); null khi đơn chưa lên chuyến hoặc chuyến chưa chạy.
     * Bảng chuyến giao thuộc migration S6-06; khi database chưa chạy migration đó thì coi như chưa có chuyến.
     */
    Map<String, Object> trip(long orderId) throws SQLException {
        if (!Boolean.TRUE.equals(query("SELECT to_regclass('public.delivery_trip_stops') IS NOT NULL AS ok").get(0).get("ok"))) return null;
        var rows = query("SELECT t.code, t.status, " + DonHangDB.iso("t.departed_at") + " AS \"departedAt\", " +
            DonHangDB.iso("t.expected_at") + " AS \"expectedAt\", d.name AS \"driverName\", d.phone AS \"driverPhone\", " +
            "v.plate AS \"vehiclePlate\", s.sequence FROM delivery_trip_stops s JOIN delivery_trips t ON t.id=s.trip_id " +
            "JOIN delivery_drivers d ON d.id=t.driver_id JOIN delivery_vehicles v ON v.id=t.vehicle_id " +
            "WHERE s.order_id=? AND t.departed_at IS NOT NULL", orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Chi tiết một đơn của đại lý: dòng hàng (không có giá sàn) và lịch sử trạng thái. */
    public Map<String, Object> order(long customerId, long id) throws SQLException {
        var rows = query(DonHangDB.HEADER + DonHangDB.FROM + " WHERE o.id=? AND o.agent_id=? AND o.status<>'DRAFT'", id, customerId);
        if (rows.isEmpty()) return null;
        Map<String, Object> order = new LinkedHashMap<>(rows.get(0));
        order.remove("approvalFlagsJson");
        var points = query("SELECT a.recipient_name AS \"recipientName\", a.phone AS \"recipientPhone\" " +
            "FROM orders o JOIN customer_addresses a ON a.id=o.delivery_address_id AND a.customer_id=o.agent_id " +
            "WHERE o.id=? AND o.agent_id=?", id, customerId);
        if (!points.isEmpty()) order.putAll(points.get(0));
        order.put("statusLabel", TrangThaiDonHang.label(order.get("status")));
        var lines = new DonHangDB().lines(id);
        for (var l : lines) { l.remove("floorPrice"); l.remove("listPrice"); l.remove("priceOverridden"); l.remove("basePrice"); }
        var deliveries=query("SELECT id, delivered_base_quantity, conversion_factor FROM order_lines WHERE order_id=?",id);
        Map<Object,Map<String,Object>> byId=new HashMap<>();
        for(var delivered:deliveries)byId.put(delivered.get("id"),delivered);
        for(var line:lines){
            var delivered=byId.get(line.get("id"));
            BigDecimal base=delivered==null?null:(BigDecimal)delivered.get("delivered_base_quantity");
            line.put("deliveredQuantity",base==null?null:base.divide((BigDecimal)delivered.get("conversion_factor"),6,java.math.RoundingMode.HALF_UP).stripTrailingZeros());
            line.put("deliveryShort",base!=null&&base.compareTo((BigDecimal)line.get("baseQuantity"))<0);
        }
        order.put("lines", lines);
        order.put("history", dealerHistory(customerId, id));
        if (TRIP_STATES.contains(String.valueOf(order.get("status")))) order.put("trip", trip(id));
        return order;
    }

    /**
     * Tiến trình hiển thị cho đại lý: giữ mốc trạng thái và thời gian, chỉ giữ lý do/người thực hiện khi do chính tài khoản
     * đại lý thao tác, hoặc lý do huỷ đơn. Lý do duyệt, ý kiến quản lý và ghi chú giữ chỗ là thông tin nội bộ.
     */
    List<Map<String, Object>> dealerHistory(long customerId, long orderId) throws SQLException {
        var history = new DonHangDB().history(orderId);
        // Cùng thứ tự với DonHangDB.history: đánh dấu mốc do tài khoản cổng của chính đại lý thực hiện.
        var marks = query("SELECT k.id IS NOT NULL AS mine FROM order_status_history h " +
            "LEFT JOIN customers k ON k.id=? AND k.portal_user_id=h.changed_by WHERE h.order_id=? ORDER BY h.changed_at, h.id",
            customerId, orderId);
        for (int i = 0; i < history.size(); i++) {
            var h = history.get(i);
            boolean mine = i < marks.size() && Boolean.TRUE.equals(marks.get(i).get("mine"));
            if (!mine) {
                h.remove("changedByName");
                if (!TrangThaiDonHang.CANCELLED.equals(h.get("toStatus"))) h.put("reason", "");
            }
        }
        return history;
    }

    /** Đại lý huỷ đơn của mình khi nhân viên chưa xác nhận (Chờ nhân viên xác nhận). */
    public void cancel(long customerId, long id, long userId, String reason) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                TrangThaiDonHang.setContext(c, userId, "Đại lý huỷ đơn: " + reason);
                var row = TruyVanDB.one(c, "SELECT code, status FROM orders WHERE id=? AND agent_id=? FOR UPDATE", id, customerId);
                if (row == null || TrangThaiDonHang.DRAFT.equals(row.get("status"))) throw new NoSuchElementException("Không tìm thấy đơn hàng");
                if (!TrangThaiDonHang.PENDING_CONFIRM.equals(row.get("status")))
                    throw new IllegalArgumentException("Đơn " + row.get("code") + " đã được nhân viên xác nhận (" +
                        TrangThaiDonHang.label(row.get("status")) + "). Hãy liên hệ nhân viên phụ trách để huỷ");
                TruyVanDB.update(c, "UPDATE orders SET status='CANCELLED', cancel_reason=? WHERE id=?", reason, id);
                GiuChoTonDB.release(c, id);
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
}
