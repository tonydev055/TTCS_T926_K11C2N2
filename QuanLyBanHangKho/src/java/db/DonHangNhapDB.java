package db;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import util.KetNoiDB;

/** Truy cập dữ liệu cho màn hình tạo đơn hàng (S3-09): tìm đại lý, tìm sản phẩm, lưu và mở lại đơn nháp. */
public class DonHangNhapDB extends CoSoDB {

    private static String like(String q) {
        // NFC để chữ có dấu gõ từ bàn phím khác nhau vẫn khớp hàm khongdau (S3-08).
        String s = q == null ? "" : java.text.Normalizer.normalize(q.trim(), java.text.Normalizer.Form.NFC);
        if (s.length() > 60) s = s.substring(0, 60);
        return "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Đại lý do nhân viên này phụ trách; trạng thái giao dịch được kiểm tra khi tạo đơn mới. */
    public boolean isAgent(long customerId, long userId) throws SQLException {
        return !query("SELECT 1 FROM customers WHERE id=? AND sales_rep_id=?", customerId, userId).isEmpty();
    }

    /** Tìm đại lý mình phụ trách theo mã, tên (không phân biệt dấu), mã số thuế hoặc số điện thoại (kèm trạng thái khoá để giao diện cảnh báo). */
    public List<Map<String, Object>> customers(String q, long userId) throws SQLException {
        String k = like(q);
        return query(
            "SELECT c.id, c.name AS \"fullName\", c.code, COALESCE(c.phone,'') AS phone, '' AS email, " +
            "c.trading_locked AS \"tradingLocked\", COALESCE(c.lock_reason,'') AS \"lockReason\" " +
            "FROM customers c WHERE c.status='ACTIVE' AND c.sales_rep_id=? AND (khongdau(c.name) LIKE khongdau(?) ESCAPE '\\' " +
            "OR c.code ILIKE ? ESCAPE '\\' OR COALESCE(c.tax_code,'') ILIKE ? ESCAPE '\\' " +
            "OR COALESCE(c.phone,'') ILIKE ? ESCAPE '\\') ORDER BY c.name LIMIT 20", userId, k, k, k, k);
    }

    /** Bảng giá hiện hành (còn hiệu lực hôm nay, phiên bản mới nhất) theo nhóm khách hàng của đại lý. */
    public Long currentPriceListId(long customerId) throws SQLException {
        var rows = query(
            "SELECT pl.id FROM price_lists pl JOIN customers u ON u.customer_group_id=pl.customer_group_id " +
            "WHERE u.id=? AND pl.active AND CURRENT_DATE BETWEEN pl.valid_from AND pl.valid_to " +
            "ORDER BY pl.version DESC, pl.id DESC LIMIT 1", customerId);
        return rows.isEmpty() ? null : ((Number) rows.get(0).get("id")).longValue();
    }

    /** Sản phẩm có giá trong bảng giá, kèm các đơn vị tính đang dùng. Giá (price) tính theo đơn vị cơ bản. */
    public List<Map<String, Object>> products(String q, long priceListId) throws SQLException {
        String k = like(q);
        var rows = query(
            "SELECT p.id, p.sku, p.name, p.base_unit AS \"baseUnit\", pll.price " +
            "FROM products p JOIN price_list_lines pll ON pll.product_id=p.id AND pll.price_list_id=? " +
            "WHERE p.active AND (p.sku ILIKE ? ESCAPE '\\' OR p.name ILIKE ? ESCAPE '\\') " +
            "ORDER BY p.sku LIMIT 20", priceListId, k, k);
        attachUnits(rows, "id");
        return rows;
    }

    /** Thông tin một dòng: sản phẩm + giá cơ bản trong bảng giá + đơn vị tính chọn. Null nếu không hợp lệ. */
    public Map<String, Object> lineInfo(long priceListId, long productId, long unitId) throws SQLException {
        var rows = query(
            "SELECT p.id, p.sku, p.name, p.category_id, pll.price, u.id AS unit_id, u.name AS unit_name, u.factor " +
            "FROM products p " +
            "JOIN price_list_lines pll ON pll.product_id=p.id AND pll.price_list_id=? " +
            "JOIN product_units u ON u.product_id=p.id AND u.id=? AND u.active " +
            "WHERE p.id=? AND p.active", priceListId, unitId, productId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void attachUnits(List<Map<String, Object>> rows, String productKey) throws SQLException {
        if (rows.isEmpty()) return;
        List<Object> ids = new ArrayList<>();
        StringJoiner marks = new StringJoiner(",");
        for (var r : rows) { ids.add(r.get(productKey)); marks.add("?"); }
        var units = query("SELECT id, product_id, name, factor FROM product_units " +
            "WHERE active AND product_id IN (" + marks + ") ORDER BY factor, id", ids.toArray());
        Map<Long, List<Map<String, Object>>> byProduct = new HashMap<>();
        for (var u : units) {
            long pid = ((Number) u.get("product_id")).longValue();
            byProduct.computeIfAbsent(pid, x -> new ArrayList<>())
                .add(new LinkedHashMap<>(Map.of("id", u.get("id"), "name", u.get("name"), "factor", u.get("factor"))));
        }
        for (var r : rows) {
            long pid = ((Number) r.get(productKey)).longValue();
            r.put("units", byProduct.getOrDefault(pid, List.of()));
        }
    }

    /** Tạo mới (orderId == null) hoặc cập nhật đơn nháp của chính người dùng. Trả về id đơn. */
    public long saveDraft(Long orderId, long userId, long customerId, long priceListId, Long pointId, String address,
                          LocalDate desiredDate, String note, BigDecimal subtotal, BigDecimal discount,
                          BigDecimal payable, List<Map<String, Object>> lines, boolean complete) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement("SELECT set_config('app.actor_id',?,true)")) {
                    p.setString(1, String.valueOf(userId));
                    p.executeQuery();
                }
                Long oldCustomerId = null;
                if (orderId != null) {
                    var old = TruyVanDB.one(c, "SELECT o.agent_id FROM orders o JOIN customers k ON k.id=o.agent_id WHERE o.id=? AND o.created_by=? AND o.status='DRAFT' AND k.sales_rep_id=? FOR UPDATE OF o,k", orderId, userId, userId);
                    if (old == null) throw new NoSuchElementException("Không tìm thấy đơn nháp để cập nhật");
                    oldCustomerId = ((Number) old.get("agent_id")).longValue();
                }
                var customer = TruyVanDB.one(c, "SELECT sales_rep_id FROM customers WHERE id=? FOR UPDATE", customerId);
                if (customer == null || !Objects.equals(customer.get("sales_rep_id"), userId))
                    throw new NoSuchElementException("Không tìm thấy đại lý phụ trách");
                // Đơn đang dở được xử lý tiếp; đổi sang đại lý khác phải kiểm tra như tạo mới.
                if (!Objects.equals(oldCustomerId, customerId)) service.KhachHangService.requireCanCreateOrder(c, customerId);
                if (pointId != null && TruyVanDB.one(c,
                    "SELECT id FROM customer_addresses WHERE id=? AND customer_id=? AND active FOR SHARE", pointId, customerId) == null)
                    throw new IllegalArgumentException("Điểm giao hàng không thuộc đại lý hoặc đã ngừng sử dụng");
                long id;
                if (orderId == null) {
                    try (PreparedStatement p = c.prepareStatement(
                            "SELECT nextval(pg_get_serial_sequence('orders','id'))");
                         ResultSet r = p.executeQuery()) {
                        r.next();
                        id = r.getLong(1);
                    }
                    try (PreparedStatement p = c.prepareStatement(
                            "INSERT INTO orders(id,code,agent_id,price_list_id,status,created_by,delivery_address,delivery_address_id," +
                            "desired_delivery_date,note,subtotal,discount_total,payable) " +
                            "VALUES(?,?,?,?,'DRAFT',?,?,?,?,?,?,?,?)")) {
                        p.setLong(1, id);
                        p.setString(2, String.format("DH%06d", id));
                        p.setLong(3, customerId);
                        p.setLong(4, priceListId);
                        p.setLong(5, userId);
                        p.setString(6, address);
                        p.setObject(7, pointId, Types.BIGINT);
                        p.setObject(8, desiredDate, Types.DATE);
                        p.setString(9, note);
                        p.setBigDecimal(10, subtotal);
                        p.setBigDecimal(11, discount);
                        p.setBigDecimal(12, payable);
                        p.executeUpdate();
                    }
                } else {
                    id = orderId;
                    try (PreparedStatement p = c.prepareStatement(
                            "UPDATE orders SET agent_id=?,price_list_id=?,delivery_address=?,delivery_address_id=?,desired_delivery_date=?," +
                            "note=?,subtotal=?,discount_total=?,payable=?,updated_at=CURRENT_TIMESTAMP " +
                            "WHERE id=? AND created_by=? AND status='DRAFT'")) {
                        p.setLong(1, customerId);
                        p.setLong(2, priceListId);
                        p.setString(3, address);
                        p.setObject(4, pointId, Types.BIGINT);
                        p.setObject(5, desiredDate, Types.DATE);
                        p.setString(6, note);
                        p.setBigDecimal(7, subtotal);
                        p.setBigDecimal(8, discount);
                        p.setBigDecimal(9, payable);
                        p.setLong(10, id);
                        p.setLong(11, userId);
                        if (p.executeUpdate() == 0) throw new NoSuchElementException("Không tìm thấy đơn nháp để cập nhật");
                    }
                    try (PreparedStatement p = c.prepareStatement("DELETE FROM order_lines WHERE order_id=?")) {
                        p.setLong(1, id);
                        p.executeUpdate();
                    }
                }
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO order_lines(order_id,product_id,unit_id,quantity,conversion_factor," +
                        "base_price,discount_amount,line_total,policy_id) VALUES(?,?,?,?,?,?,?,?,?)")) {
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
                        p.addBatch();
                    }
                    p.executeBatch();
                }
                if (complete) TruyVanDB.update(c, "UPDATE orders SET status='COMPLETED',updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
                c.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Mở lại một đơn nháp của chính người dùng (kèm dòng hàng và các đơn vị tính để sửa tiếp). */
    public Map<String, Object> load(long id, long userId) throws SQLException {
        var rows = query(
            "SELECT o.id, o.code, o.status, o.agent_id AS \"customerId\", u.name AS \"customerName\", " +
            "COALESCE(u.phone,'') AS \"customerPhone\", o.delivery_address AS \"deliveryAddress\", o.delivery_address_id AS \"deliveryPointId\", " +
            "u.trading_locked AS \"tradingLocked\", COALESCE(u.lock_reason,'') AS \"lockReason\", " +
            "o.desired_delivery_date AS \"desiredDate\", o.note, o.subtotal, o.discount_total AS discount, o.payable " +
            "FROM orders o LEFT JOIN customers u ON u.id=o.agent_id WHERE o.id=? AND o.created_by=? AND u.sales_rep_id=?", id, userId, userId);
        if (rows.isEmpty()) return null;
        Map<String, Object> order = new LinkedHashMap<>(rows.get(0));
        var lines = query(
            "SELECT l.product_id AS \"productId\", p.sku, p.name, l.unit_id AS \"unitId\", trim_scale(l.quantity) AS quantity " +
            "FROM order_lines l JOIN products p ON p.id=l.product_id WHERE l.order_id=? ORDER BY l.id", id);
        attachUnits(lines, "productId");
        order.put("lines", lines);
        return order;
    }

    public List<Map<String, Object>> listDrafts(long userId) throws SQLException {
        return query(
            "SELECT o.id, o.code, COALESCE(u.name,'') AS \"customerName\", o.payable, o.updated_at AS \"updatedAt\", " +
            "(SELECT count(*) FROM order_lines l WHERE l.order_id=o.id) AS \"lineCount\" " +
            "FROM orders o LEFT JOIN customers u ON u.id=o.agent_id " +
            "WHERE o.created_by=? AND u.sales_rep_id=? AND o.status='DRAFT' ORDER BY o.updated_at DESC LIMIT 50", userId, userId);
    }
}
