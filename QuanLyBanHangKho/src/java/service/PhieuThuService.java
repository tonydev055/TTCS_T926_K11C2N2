package service;

import db.PhieuThuDB;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.*;

public final class PhieuThuService {
    private static final Set<String> METHODS = Set.of("CASH", "BANK_TRANSFER", "OTHER");
    private final PhieuThuDB db = new PhieuThuDB();

    public List<Map<String, Object>> list() throws SQLException { return db.findAll(); }

    public Map<String, Object> detail(long id) throws SQLException {
        Map<String, Object> row = db.findById(id);
        if (row == null) throw new NoSuchElementException("Không tìm thấy phiếu thu");
        return row;
    }

    public Map<String, Object> create(Map<String, Object> input, long actorId) throws SQLException {
        long customerId = positiveLong(input.get("customer_id"), "Vui lòng chọn khách hàng");
        Long orderId = optionalLong(input.get("order_id"), "Mã đơn hàng không hợp lệ");
        BigDecimal amount = decimal(input.get("amount"));
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Số tiền thu phải lớn hơn 0");
        if (amount.scale() > 2) throw new IllegalArgumentException("Số tiền chỉ được có tối đa 2 chữ số thập phân");
        if (amount.precision() > 18) throw new IllegalArgumentException("Số tiền vượt giới hạn cho phép");
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        String method = text(input.get("payment_method"), "CASH").toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) throw new IllegalArgumentException("Hình thức thu không hợp lệ");
        String reference = bounded(input.get("reference_code"), 120, "Mã tham chiếu");
        String note = bounded(input.get("note"), 2000, "Ghi chú");
        return db.create(customerId, orderId, amount, method, reference, note, actorId);
    }

    private static long positiveLong(Object raw, String message) {
        Long value = optionalLong(raw, message);
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }
    private static Long optionalLong(Object raw, String message) {
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        try {
            long value = new BigDecimal(String.valueOf(raw)).longValueExact();
            if (value <= 0) throw new ArithmeticException();
            return value;
        } catch (RuntimeException e) { throw new IllegalArgumentException(message); }
    }
    private static BigDecimal decimal(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        try { return new BigDecimal(String.valueOf(raw).trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Số tiền không hợp lệ"); }
    }
    private static String text(Object raw, String fallback) {
        return raw == null ? fallback : String.valueOf(raw).trim();
    }
    private static String bounded(Object raw, int max, String label) {
        String value = raw == null ? "" : String.valueOf(raw).trim();
        if (value.length() > max) throw new IllegalArgumentException(label + " không được vượt quá " + max + " ký tự");
        return value.isEmpty() ? null : value;
    }
}
