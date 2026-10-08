package db;

import java.sql.*;
import java.util.*;
import util.KetNoiDB;

/** Điểm giao hàng của đại lý (một đại lý có nhiều điểm, đúng một điểm mặc định). Xóa là ẩn (active=false) vì đơn cũ còn tham chiếu. */
public class DiemGiaoDB extends CoSoDB {

    private interface Work<T> { T run(Connection c) throws SQLException; }

    /** Khóa dòng đại lý để hai thao tác đổi "mặc định" cùng lúc không đạp lên nhau. */
    private <T> T tx(long customerId, Work<T> w) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE")) {
                    p.setLong(1, customerId);
                    p.executeQuery();
                }
                T r = w.run(c);
                c.commit();
                return r;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static int exec(Connection c, String sql, Object... a) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < a.length; i++) p.setObject(i + 1, a[i]);
            return p.executeUpdate();
        }
    }

    /** Đại lý là tài khoản đang hoạt động có vai trò CUSTOMER. */
    public boolean isAgent(long userId) throws SQLException {
        return !query("SELECT 1 FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id " +
            "WHERE u.id=? AND u.active AND r.code='CUSTOMER'", userId).isEmpty();
    }

    private static String like(String q) {
        String s = q == null ? "" : q.trim();
        if (s.length() > 60) s = s.substring(0, 60);
        return "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Tìm đại lý theo tên, tên đăng nhập, email hoặc số điện thoại. */
    public List<Map<String, Object>> customers(String q) throws SQLException {
        String k = like(q);
        return query(
            "SELECT u.id, u.full_name AS \"fullName\", COALESCE(u.phone,'') AS phone, u.email, " +
            "(SELECT count(*) FROM delivery_points d WHERE d.customer_id=u.id AND d.active) AS \"pointCount\" " +
            "FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id AND r.code='CUSTOMER' " +
            "WHERE u.active AND (u.full_name ILIKE ? ESCAPE '\\' OR u.username ILIKE ? ESCAPE '\\' " +
            "OR u.email ILIKE ? ESCAPE '\\' OR COALESCE(u.phone,'') ILIKE ? ESCAPE '\\') " +
            "ORDER BY u.full_name LIMIT 20", k, k, k, k);
    }

    public List<Map<String, Object>> listByCustomer(long customerId) throws SQLException {
        return query("SELECT id, receiver_name AS \"receiverName\", phone, address, route_note AS \"routeNote\", " +
            "is_default AS \"isDefault\" FROM delivery_points WHERE customer_id=? AND active " +
            "ORDER BY is_default DESC, id", customerId);
    }

    /** Điểm giao (còn dùng) thuộc đúng đại lý này; null nếu không. Dùng khi lưu đơn hàng. */
    public Map<String, Object> findOfCustomer(long pointId, long customerId) throws SQLException {
        var rows = query("SELECT id, receiver_name AS \"receiverName\", phone, address FROM delivery_points " +
            "WHERE id=? AND customer_id=? AND active", pointId, customerId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long create(long customerId, String receiver, String phone, String address, String note,
                       boolean makeDefault) throws SQLException {
        return tx(customerId, c -> {
            long count;
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT count(*) FROM delivery_points WHERE customer_id=? AND active")) {
                p.setLong(1, customerId);
                try (ResultSet r = p.executeQuery()) { r.next(); count = r.getLong(1); }
            }
            boolean def = makeDefault || count == 0;   // điểm đầu tiên tự thành mặc định
            if (def) exec(c, "UPDATE delivery_points SET is_default=FALSE WHERE customer_id=? AND is_default", customerId);
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO delivery_points(customer_id,receiver_name,phone,address,route_note,is_default) " +
                    "VALUES(?,?,?,?,?,?) RETURNING id")) {
                p.setLong(1, customerId); p.setString(2, receiver); p.setString(3, phone);
                p.setString(4, address); p.setString(5, note); p.setBoolean(6, def);
                try (ResultSet r = p.executeQuery()) { r.next(); return r.getLong(1); }
            }
        });
    }

    public void update(long id, long customerId, String receiver, String phone, String address, String note,
                       boolean makeDefault) throws SQLException {
        tx(customerId, c -> {
            if (makeDefault) exec(c, "UPDATE delivery_points SET is_default=FALSE WHERE customer_id=? AND is_default", customerId);
            int n = exec(c, "UPDATE delivery_points SET receiver_name=?,phone=?,address=?,route_note=?," +
                "is_default=(is_default OR ?),updated_at=CURRENT_TIMESTAMP WHERE id=? AND customer_id=? AND active",
                receiver, phone, address, note, makeDefault, id, customerId);
            if (n == 0) throw new NoSuchElementException("Không tìm thấy điểm giao hàng");
            return null;
        });
    }

    public void setDefault(long id, long customerId) throws SQLException {
        tx(customerId, c -> {
            exec(c, "UPDATE delivery_points SET is_default=FALSE WHERE customer_id=? AND is_default", customerId);
            int n = exec(c, "UPDATE delivery_points SET is_default=TRUE,updated_at=CURRENT_TIMESTAMP " +
                "WHERE id=? AND customer_id=? AND active", id, customerId);
            if (n == 0) throw new NoSuchElementException("Không tìm thấy điểm giao hàng"); // rollback trả lại mặc định cũ
            return null;
        });
    }

    public void remove(long id, long customerId) throws SQLException {
        tx(customerId, c -> {
            int n = exec(c, "UPDATE delivery_points SET active=FALSE,is_default=FALSE,updated_at=CURRENT_TIMESTAMP " +
                "WHERE id=? AND customer_id=? AND active", id, customerId);
            if (n == 0) throw new NoSuchElementException("Không tìm thấy điểm giao hàng");
            // Vừa xóa điểm mặc định thì chuyển mặc định sang điểm còn lại cũ nhất.
            exec(c, "UPDATE delivery_points SET is_default=TRUE WHERE id=(SELECT id FROM delivery_points " +
                "WHERE customer_id=? AND active ORDER BY id LIMIT 1) AND NOT EXISTS " +
                "(SELECT 1 FROM delivery_points WHERE customer_id=? AND active AND is_default)", customerId, customerId);
            return null;
        });
    }
}
