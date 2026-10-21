package db;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;

/**
 * Tồn khả dụng và giữ chỗ tồn cho đơn hàng (S4-03). Số lượng theo đơn vị cơ bản của sản phẩm.
 *
 * Tồn khả dụng = inventory.on_hand − inventory.reserved. Giữ chỗ khoá các dòng tồn (SELECT ... FOR UPDATE, theo thứ tự
 * product_id để tránh deadlock) rồi mới kiểm tra và cộng reserved, nên hai đơn chốt cùng lúc trên SKU sắp hết luôn cho
 * kết quả đúng; ràng buộc inventory_reserved_within_on_hand là chốt chặn cuối ở database.
 */
public final class GiuChoTonDB {

    private GiuChoTonDB() {}

    static String qty(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return (s.scale() < 0 ? s.setScale(0) : s).toPlainString().replace('.', ',');
    }

    /** Tồn khả dụng theo sản phẩm tại một kho; sản phẩm chưa có dòng tồn coi như 0. */
    public static Map<Long, BigDecimal> available(long warehouseId, Collection<Long> productIds) throws SQLException {
        Map<Long, BigDecimal> out = new HashMap<>();
        for (Long id : productIds) out.put(id, BigDecimal.ZERO);
        if (productIds.isEmpty()) return out;
        StringJoiner marks = new StringJoiner(",");
        List<Object> args = new ArrayList<>();
        args.add(warehouseId);
        for (Long id : productIds) { marks.add("?"); args.add(id); }
        for (var r : new CoSoDB().query("SELECT product_id, on_hand - reserved AS available FROM inventory " +
                "WHERE warehouse_id=? AND product_id IN (" + marks + ")", args.toArray()))
            out.put(((Number) r.get("product_id")).longValue(), (BigDecimal) r.get("available"));
        return out;
    }

    /** Kho phục vụ của đại lý: {id, name}; null nếu chưa gán hoặc kho đã ngừng hoạt động. */
    public static Map<String, Object> customerWarehouse(long customerId) throws SQLException {
        var rows = new CoSoDB().query("SELECT w.id, w.name FROM customers c JOIN warehouses w ON w.id=c.warehouse_id " +
            "WHERE c.id=? AND w.active", customerId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Giữ chỗ toàn bộ dòng hàng của đơn tại kho của đơn (orders.warehouse_id), trong giao dịch c.
     * Thiếu tồn ở bất kỳ sản phẩm nào thì báo lỗi kèm số lượng tối đa còn giữ được và không giữ gì.
     */
    public static void reserve(Connection c, long orderId) throws SQLException {
        var order = TruyVanDB.one(c, "SELECT o.code, o.warehouse_id, w.name AS warehouse_name FROM orders o " +
            "LEFT JOIN warehouses w ON w.id=o.warehouse_id WHERE o.id=?", orderId);
        if (order == null) throw new NoSuchElementException("Không tìm thấy đơn hàng");
        if (order.get("warehouse_id") == null) throw new IllegalArgumentException(
            "Đơn " + order.get("code") + " chưa có kho phục vụ. Hãy gán kho phục vụ cho đại lý trong hồ sơ đại lý");
        long warehouse = ((Number) order.get("warehouse_id")).longValue();
        if (!TruyVanDB.rows(c, "SELECT 1 FROM stock_reservations WHERE order_id=? AND status='ACTIVE'", orderId).isEmpty())
            return; // đã giữ chỗ (gọi lặp lại) — không giữ hai lần
        var needs = TruyVanDB.rows(c,
            "SELECT l.product_id, p.sku, p.name, p.base_unit, sum(l.base_quantity) AS qty FROM order_lines l " +
            "JOIN products p ON p.id=l.product_id WHERE l.order_id=? GROUP BY l.product_id, p.sku, p.name, p.base_unit " +
            "ORDER BY l.product_id", orderId);
        List<String> shortages = new ArrayList<>();
        for (var need : needs) {
            long product = ((Number) need.get("product_id")).longValue();
            BigDecimal qty = (BigDecimal) need.get("qty");
            // Khoá dòng tồn theo thứ tự product_id; giao dịch khác giữ cùng SKU phải chờ tới khi giao dịch này kết thúc.
            var stock = TruyVanDB.one(c, "SELECT on_hand - reserved AS available FROM inventory " +
                "WHERE warehouse_id=? AND product_id=? FOR UPDATE", warehouse, product);
            BigDecimal available = stock == null ? BigDecimal.ZERO : (BigDecimal) stock.get("available");
            if (available.compareTo(qty) < 0) {
                shortages.add(need.get("sku") + " — " + need.get("name") + ": cần " + qty(qty) + " " + need.get("base_unit") +
                    ", chỉ còn " + qty(available.max(BigDecimal.ZERO)) + " " + need.get("base_unit"));
                continue;
            }
            TruyVanDB.update(c, "UPDATE inventory SET reserved=reserved+?, updated_at=CURRENT_TIMESTAMP " +
                "WHERE warehouse_id=? AND product_id=?", qty, warehouse, product);
            TruyVanDB.update(c, "INSERT INTO stock_reservations(order_id,warehouse_id,product_id,quantity) VALUES(?,?,?,?)",
                orderId, warehouse, product, qty);
        }
        if (!shortages.isEmpty()) throw new IllegalArgumentException("Không đủ tồn khả dụng tại " + order.get("warehouse_name") +
            " để giữ hàng cho đơn " + order.get("code") + ": " + String.join("; ", shortages));
    }

    /** Nhả mọi phần đang giữ chỗ của đơn (huỷ đơn), trong giao dịch c. */
    public static void release(Connection c, long orderId) throws SQLException {
        var rows = TruyVanDB.rows(c, "SELECT id, warehouse_id, product_id, quantity FROM stock_reservations " +
            "WHERE order_id=? AND status='ACTIVE' ORDER BY product_id FOR UPDATE", orderId);
        for (var r : rows) {
            TruyVanDB.update(c, "UPDATE inventory SET reserved=reserved-?, updated_at=CURRENT_TIMESTAMP " +
                "WHERE warehouse_id=? AND product_id=?", r.get("quantity"), r.get("warehouse_id"), r.get("product_id"));
            TruyVanDB.update(c, "UPDATE stock_reservations SET status='RELEASED', released_at=CURRENT_TIMESTAMP WHERE id=?",
                r.get("id"));
        }
    }

    /** Số lượng tối đa theo đơn vị đã chọn từ một lượng tồn (đơn vị cơ bản), làm tròn xuống. */
    public static BigDecimal inUnit(BigDecimal baseQty, BigDecimal factor) {
        if (baseQty.signum() <= 0) return BigDecimal.ZERO;
        // Số lượng bán có tối đa 6 số lẻ và phải quy đổi thành số nguyên
        // đơn vị cơ bản. Tìm bước số lượng nhỏ nhất đáp ứng cả hai điều kiện.
        BigDecimal normalized = factor.stripTrailingZeros();
        int scale = Math.max(0, normalized.scale());
        java.math.BigInteger numerator = normalized.setScale(scale).unscaledValue();
        java.math.BigInteger denominator = java.math.BigInteger.TEN.pow(scale + 6);
        BigDecimal step = new BigDecimal(denominator.divide(denominator.gcd(numerator)), 6);
        BigDecimal baseStep = step.multiply(factor);
        return baseQty.divide(baseStep, 0, RoundingMode.FLOOR).multiply(step).stripTrailingZeros();
    }
}
