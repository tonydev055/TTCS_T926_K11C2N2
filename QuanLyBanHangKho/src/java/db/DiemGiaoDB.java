package db;

import java.sql.*;
import java.util.*;
import util.KetNoiDB;

/**
 * Điểm giao hàng của đại lý, dùng chung bảng customers / customer_addresses với hồ sơ đại lý (S3-03, S3-04).
 * Một đại lý có nhiều điểm, đúng một điểm mặc định. Xóa là ẩn (active=false) vì đơn cũ còn tham chiếu.
 * Nhân viên kinh doanh chỉ thao tác trên đại lý mình phụ trách.
 */
public class DiemGiaoDB extends CoSoDB {

    private interface Work<T> { T run(Connection c) throws SQLException; }

    /**
     * Khóa dòng đại lý để hai thao tác đổi "mặc định" cùng lúc không đạp lên nhau.
     * Đặt người thực hiện để trigger nhật ký ghi đúng actor trong transaction này.
     */
    private <T> T tx(long customerId, long actor, Work<T> w) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement("SELECT set_config('app.actor_id',?,true)")) {
                    p.setString(1, String.valueOf(actor));
                    p.executeQuery();
                }
                try (PreparedStatement p = c.prepareStatement("SELECT id FROM customers WHERE id=? FOR UPDATE")) {
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

    /** Đại lý do nhân viên này phụ trách, bao gồm hồ sơ có đơn đang xử lý. */
    public boolean isAgent(long customerId, long userId) throws SQLException {
        return !query("SELECT 1 FROM customers WHERE id=? AND sales_rep_id=?", customerId, userId).isEmpty();
    }

    private static String like(String q) {
        String s = q == null ? "" : q.trim();
        if (s.length() > 60) s = s.substring(0, 60);
        return "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Tìm đại lý mình phụ trách theo mã, tên, mã số thuế hoặc số điện thoại. */
    public List<Map<String, Object>> customers(String q, long userId) throws SQLException {
        String k = like(q);
        return query(
            "SELECT c.id, c.name AS \"fullName\", c.code, COALESCE(c.phone,'') AS phone, '' AS email, " +
            "(SELECT count(*) FROM customer_addresses d WHERE d.customer_id=c.id AND d.active) AS \"pointCount\" " +
            "FROM customers c WHERE c.status='ACTIVE' AND c.sales_rep_id=? AND (c.name ILIKE ? ESCAPE '\\' " +
            "OR c.code ILIKE ? ESCAPE '\\' OR COALESCE(c.tax_code,'') ILIKE ? ESCAPE '\\' " +
            "OR COALESCE(c.phone,'') ILIKE ? ESCAPE '\\') ORDER BY c.name LIMIT 20", userId, k, k, k, k);
    }

    public List<Map<String, Object>> listByCustomer(long customerId) throws SQLException {
        return query("SELECT id, recipient_name AS \"receiverName\", phone, address, directions AS \"routeNote\", " +
            "is_default AS \"isDefault\" FROM customer_addresses WHERE customer_id=? AND active " +
            "ORDER BY is_default DESC, id", customerId);
    }

    /** Điểm giao (còn dùng) thuộc đúng đại lý này; null nếu không. Dùng khi lưu đơn hàng. */
    public Map<String, Object> findOfCustomer(long pointId, long customerId) throws SQLException {
        var rows = query("SELECT id, recipient_name AS \"receiverName\", phone, address FROM customer_addresses " +
            "WHERE id=? AND customer_id=? AND active", pointId, customerId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long create(long customerId, String receiver, String phone, String address, String note,
                       boolean makeDefault, long actor) throws SQLException {
        return tx(customerId, actor, c -> {
            long count;
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT count(*) FROM customer_addresses WHERE customer_id=? AND active")) {
                p.setLong(1, customerId);
                try (ResultSet r = p.executeQuery()) { r.next(); count = r.getLong(1); }
            }
            boolean def = makeDefault || count == 0;   // điểm đầu tiên tự thành mặc định
            if (def) exec(c, "UPDATE customer_addresses SET is_default=FALSE WHERE customer_id=? AND is_default", customerId);
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO customer_addresses(customer_id,recipient_name,phone,address,directions,is_default) " +
                    "VALUES(?,?,?,?,?,?) RETURNING id")) {
                p.setLong(1, customerId); p.setString(2, receiver); p.setString(3, phone);
                p.setString(4, address); p.setString(5, note); p.setBoolean(6, def);
                try (ResultSet r = p.executeQuery()) { r.next(); return r.getLong(1); }
            }
        });
    }

    public void update(long id, long customerId, String receiver, String phone, String address, String note,
                       boolean makeDefault, long actor) throws SQLException {
        tx(customerId, actor, c -> {
            if (makeDefault) exec(c, "UPDATE customer_addresses SET is_default=FALSE WHERE customer_id=? AND is_default", customerId);
            int n = exec(c, "UPDATE customer_addresses SET recipient_name=?,phone=?,address=?,directions=?," +
                "is_default=(is_default OR ?),updated_at=CURRENT_TIMESTAMP WHERE id=? AND customer_id=? AND active",
                receiver, phone, address, note, makeDefault, id, customerId);
            if (n == 0) throw new NoSuchElementException("Không tìm thấy điểm giao hàng");
            return null;
        });
    }

    public void setDefault(long id, long customerId, long actor) throws SQLException {
        tx(customerId, actor, c -> {
            exec(c, "UPDATE customer_addresses SET is_default=FALSE WHERE customer_id=? AND is_default", customerId);
            int n = exec(c, "UPDATE customer_addresses SET is_default=TRUE,updated_at=CURRENT_TIMESTAMP " +
                "WHERE id=? AND customer_id=? AND active", id, customerId);
            if (n == 0) throw new NoSuchElementException("Không tìm thấy điểm giao hàng"); // rollback trả lại mặc định cũ
            return null;
        });
    }

    public void remove(long id, long customerId, long actor) throws SQLException {
        tx(customerId, actor, c -> {
            int n = exec(c, "UPDATE customer_addresses SET active=FALSE,is_default=FALSE,updated_at=CURRENT_TIMESTAMP " +
                "WHERE id=? AND customer_id=? AND active", id, customerId);
            if (n == 0) throw new NoSuchElementException("Không tìm thấy điểm giao hàng");
            // Vừa xóa điểm mặc định thì chuyển mặc định sang điểm còn lại cũ nhất.
            exec(c, "UPDATE customer_addresses SET is_default=TRUE WHERE id=(SELECT id FROM customer_addresses " +
                "WHERE customer_id=? AND active ORDER BY id LIMIT 1) AND NOT EXISTS " +
                "(SELECT 1 FROM customer_addresses WHERE customer_id=? AND active AND is_default)", customerId, customerId);
            return null;
        });
    }
}
