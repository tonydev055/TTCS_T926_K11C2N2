package db;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import util.KetNoiDB;

/** Lưu khoản thu đã xác nhận; không sửa/xóa phiếu qua API. */
public final class PhieuThuDB {
    private static final String SELECT =
        "SELECT p.id, p.receipt_code, p.customer_id, c.code AS customer_code, " +
        "c.name AS customer_name, p.order_id, o.code AS order_code, p.amount, " +
        "p.payment_method, p.reference_code, p.note, p.received_by, " +
        "u.full_name AS collector_name, p.created_at " +
        "FROM cash_receipts p JOIN customers c ON c.id=p.customer_id " +
        "LEFT JOIN orders o ON o.id=p.order_id LEFT JOIN users u ON u.id=p.received_by ";

    public List<Map<String, Object>> findAll() throws SQLException {
        try (Connection c = KetNoiDB.getConnection();
             PreparedStatement p = c.prepareStatement(SELECT + " ORDER BY p.created_at DESC, p.id DESC LIMIT 500");
             ResultSet rs = p.executeQuery()) {
            return rows(rs);
        }
    }

    public Map<String, Object> findById(long id) throws SQLException {
        try (Connection c = KetNoiDB.getConnection();
             PreparedStatement p = c.prepareStatement(SELECT + " WHERE p.id=?")) {
            p.setLong(1, id);
            try (ResultSet rs = p.executeQuery()) {
                List<Map<String, Object>> result = rows(rs);
                return result.isEmpty() ? null : result.get(0);
            }
        }
    }

    public Map<String, Object> create(long customerId, Long orderId, BigDecimal amount,
            String method, String reference, String note, long actorId) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM customers WHERE id=?")) {
                    p.setLong(1, customerId);
                    try (ResultSet rs = p.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Khách hàng không tồn tại");
                    }
                }
                if (orderId != null) {
                    try (PreparedStatement p = c.prepareStatement("SELECT customer_id FROM orders WHERE id=?")) {
                        p.setLong(1, orderId);
                        try (ResultSet rs = p.executeQuery()) {
                            if (!rs.next()) throw new IllegalArgumentException("Đơn hàng không tồn tại");
                            if (rs.getLong(1) != customerId)
                                throw new IllegalArgumentException("Đơn hàng không thuộc khách hàng đã chọn");
                        }
                    }
                }
                long id;
                String sql = "INSERT INTO cash_receipts(customer_id,order_id,amount,payment_method,reference_code,note,received_by) " +
                    "VALUES(?,?,?,?,?,?,?) RETURNING id";
                try (PreparedStatement p = c.prepareStatement(sql)) {
                    p.setLong(1, customerId);
                    if (orderId == null) p.setNull(2, Types.BIGINT); else p.setLong(2, orderId);
                    p.setBigDecimal(3, amount);
                    p.setString(4, method);
                    p.setString(5, reference);
                    p.setString(6, note);
                    p.setLong(7, actorId);
                    try (ResultSet rs = p.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Không tạo được phiếu thu");
                        id = rs.getLong(1);
                    }
                }
                Map<String, Object> result;
                try (PreparedStatement p = c.prepareStatement(SELECT + " WHERE p.id=?")) {
                    p.setLong(1, id);
                    try (ResultSet rs = p.executeQuery()) {
                        List<Map<String, Object>> found = rows(rs);
                        if (found.isEmpty()) throw new SQLException("Không đọc lại được phiếu thu vừa tạo");
                        result = found.get(0);
                    }
                }
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static List<Map<String, Object>> rows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) row.put(meta.getColumnLabel(i), rs.getObject(i));
            out.add(row);
        }
        return out;
    }
}
