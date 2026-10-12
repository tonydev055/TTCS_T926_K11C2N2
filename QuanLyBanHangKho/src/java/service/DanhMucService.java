package service;
import db.TruyVanDB;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
public class DanhMucService {
    public static String text(Map<String, Object> data, String key, boolean required, int max) {
        String x = Objects.toString(data.get(key), "").trim();
        if ((required && x.isEmpty()) || x.length() > max) throw new IllegalArgumentException(
            "Trường " + key + " không hợp lệ (tối đa " + max + " ký tự)"
        );
        return x;
    }
    public static long integer(Map<String, Object> data, String key) {
        try {
            return new BigDecimal(Objects.toString(data.get(key), "0")).longValueExact();
        } catch (Exception e) {
            throw new IllegalArgumentException("Trường " + key + " phải là số nguyên");
        }
    }
    public static BigDecimal decimal(
        Map<String, Object> data,
        String key,
        boolean positive,
        int scale
    ) {
        try {
            BigDecimal v = new BigDecimal(Objects.toString(data.get(key), ""));
            if (
                v.signum() < (positive ? 1 : 0) ||
                v.scale() > scale ||
                v.precision() - v.scale() > 12
            ) throw new Exception();
            return v;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Trường " +
                    key +
                    " phải là số " +
                    (positive ? "dương" : "không âm") +
                    ", tối đa " +
                    scale +
                    " chữ số thập phân"
            );
        }
    }
    public static boolean active(Map<String, Object> data) {
        Object v = data.getOrDefault("active", true);
        if (!(v instanceof Boolean)) throw new IllegalArgumentException("Trạng thái không hợp lệ");
        return (Boolean) v;
    }
    public static String phone(String value, boolean required) {
        String p = value.trim().replaceAll("[ .-]", "");
        if (p.isEmpty() && !required) return p;
        if (!p.matches("(?:0|\\+84)[35789][0-9]{8}")) throw new IllegalArgumentException(
            "Số điện thoại Việt Nam không hợp lệ"
        );
        return p;
    }
    public static void begin(Connection c, long actor) throws SQLException {
        c.setAutoCommit(false);
        TruyVanDB.rows(c, "SELECT pg_advisory_xact_lock(260902)");
        TruyVanDB.rows(c, "SELECT set_config('app.actor_id',?,true)", String.valueOf(actor));
    }
    public static String error(Exception e) {
        if (e instanceof SQLException sql) {
            return switch (Objects.toString(sql.getSQLState(), "")) {
                case "23505" -> "Mã đã tồn tại. Vui lòng dùng mã khác";
                case "23503" -> "Dữ liệu đã được sử dụng, không thể xóa; hãy chuyển sang ngừng hoạt động";
                case "23514", "23502", "22003" -> "Dữ liệu không đáp ứng điều kiện hợp lệ";
                default -> "Không thể xử lý dữ liệu. Vui lòng thử lại";
            };
        }
        return e.getMessage() == null ? "Không thể xử lý yêu cầu" : e.getMessage();
    }
}
