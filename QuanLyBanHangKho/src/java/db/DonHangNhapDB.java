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

    /**
     * Sản phẩm đang kinh doanh khớp từ khoá, kèm các đơn vị tính đang dùng. Giá (price) và giá sàn tính theo đơn vị cơ bản.
     * S4-01: trả cả sản phẩm chưa có giá trong bảng giá (priced=false) để giao diện báo rõ lý do không thêm được.
     */
    public List<Map<String, Object>> products(String q, long priceListId) throws SQLException {
        String k = like(q);
        var rows = query(
            "SELECT p.id, p.sku, p.name, p.base_unit AS \"baseUnit\", pll.price, pll.floor_price AS \"floorPrice\", " +
            "(pll.id IS NOT NULL) AS priced " +
            "FROM products p LEFT JOIN price_list_lines pll ON pll.product_id=p.id AND pll.price_list_id=? " +
            "WHERE p.active AND (p.sku ILIKE ? ESCAPE '\\' OR p.name ILIKE ? ESCAPE '\\') " +
            "ORDER BY (pll.id IS NULL), p.sku LIMIT 20", priceListId, k, k);
        attachUnits(rows, "id");
        return rows;
    }

    /**
     * Công nợ của đại lý (S4-02) từ view customer_credit_status. blocked=true khi có khoản quá hạn:
     * khi đó không được tạo đơn mới hay gửi đơn.
     */
    public Map<String, Object> creditStatus(long customerId) throws SQLException {
        var rows = query(
            "SELECT c.code, s.credit_limit, s.credit_days, s.outstanding, s.available, s.overdue_amount, " +
            "s.overdue_orders, s.max_overdue_days FROM customer_credit_status s JOIN customers c ON c.id=s.customer_id " +
            "WHERE s.customer_id=?", customerId);
        if (rows.isEmpty()) throw new NoSuchElementException("Không tìm thấy đại lý");
        var r = rows.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("creditLimit", r.get("credit_limit"));
        out.put("creditDays", r.get("credit_days"));
        out.put("outstanding", r.get("outstanding"));
        out.put("available", r.get("available"));
        out.put("overdueAmount", r.get("overdue_amount"));
        out.put("overdueOrders", r.get("overdue_orders"));
        out.put("maxOverdueDays", r.get("max_overdue_days"));
        boolean blocked = ((Number) r.get("overdue_orders")).intValue() > 0;
        out.put("blocked", blocked);
        out.put("blockReason", blocked
            ? "Đại lý " + r.get("code") + " có " + r.get("overdue_orders") + " đơn nợ quá hạn (quá " + r.get("max_overdue_days") +
              " ngày so với " + r.get("credit_days") + " ngày được nợ, tổng " +
              service.TaoDonHangService.tien((java.math.BigDecimal) r.get("overdue_amount")) +
              "). Không thể tạo hoặc gửi đơn mới cho tới khi thu hồi công nợ"
            : "");
        return out;
    }

    /** Tên hiển thị "SKU — Tên" của sản phẩm để báo lỗi; null nếu không có. */
    public String productName(long productId) throws SQLException {
        var rows = query("SELECT sku || ' — ' || name AS n FROM products WHERE id=?", productId);
        return rows.isEmpty() ? null : (String) rows.get(0).get("n");
    }

    /** Thông tin một dòng: sản phẩm + giá cơ bản trong bảng giá + đơn vị tính chọn. Null nếu không hợp lệ. */
    public Map<String, Object> lineInfo(long priceListId, long productId, long unitId) throws SQLException {
        var rows = query(
            "SELECT p.id, p.sku, p.name, p.category_id, pll.price, pll.floor_price, u.id AS unit_id, u.name AS unit_name, u.factor " +
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

    /**
     * Tạo mới (orderId == null) hoặc cập nhật đơn nháp của chính người dùng. Trả về id đơn.
     * flags != null nghĩa là gửi đơn: không có lý do cần duyệt thì hệ thống tự duyệt, ngược lại chuyển Chờ duyệt.
     */
    public long saveDraft(Long orderId, long userId, long customerId, long priceListId, Long pointId, String address,
                          LocalDate desiredDate, String note, BigDecimal subtotal, BigDecimal discount,
                          BigDecimal payable, List<Map<String, Object>> lines, List<Map<String, Object>> flags) throws SQLException {
        boolean complete = flags != null;
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
                        "base_price,discount_amount,line_total,policy_id,list_price,floor_price,price_overridden) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
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
                        p.setBoolean(12, Boolean.TRUE.equals(l.get("priceOverridden")));
                        p.addBatch();
                    }
                    p.executeBatch();
                }
                if (complete) submit(c, id, userId, flags, "Nhân viên gửi đơn");
                c.commit();
                return id;
            } catch (SQLException e) {
                c.rollback();
                throw service.TrangThaiDonHang.rethrow(e);
            } catch (RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Chuyển đơn đang ở Nháp/Chờ xác nhận sang bước duyệt trong giao dịch c (S4-01, S4-06).
     * Không có lý do cần duyệt: hệ thống tự duyệt. Có: Chờ duyệt, lưu lý do để quản lý xem. Trả về trạng thái mới.
     */
    public static String submit(Connection c, long id, long actor, List<Map<String, Object>> flags, String who) throws SQLException {
        // Khoá đại lý trước khi đọc lại công nợ: báo giá trước giao dịch có thể đã cũ
        // khi một đơn khác vừa được duyệt. Mọi kênh gửi đơn dùng cùng chốt kiểm tra.
        var order = TruyVanDB.one(c, "SELECT agent_id, payable FROM orders WHERE id=? FOR UPDATE", id);
        if (order == null) throw new NoSuchElementException("Không tìm thấy đơn hàng");
        TruyVanDB.one(c, "SELECT id FROM customers WHERE id=? FOR UPDATE", order.get("agent_id"));
        var credit = TruyVanDB.one(c,
            "SELECT outstanding, credit_limit FROM customer_credit_status WHERE customer_id=?", order.get("agent_id"));
        BigDecimal payable = (BigDecimal) order.get("payable");
        BigDecimal limit = (BigDecimal) credit.get("credit_limit");
        BigDecimal after = ((BigDecimal) credit.get("outstanding")).add(payable);
        flags = new ArrayList<>(flags);
        flags.removeIf(flag -> "OVER_CREDIT".equals(flag.get("code")));
        if (payable.signum() > 0 && after.compareTo(limit) > 0) {
            Map<String, Object> flag = new LinkedHashMap<>();
            flag.put("code", "OVER_CREDIT");
            flag.put("message", "Vượt hạn mức công nợ " + service.TaoDonHangService.tien(after.subtract(limit)) +
                ": công nợ hiện tại " + service.TaoDonHangService.tien((BigDecimal) credit.get("outstanding")) +
                " + đơn này " + service.TaoDonHangService.tien(payable) + ", hạn mức " +
                service.TaoDonHangService.tien(limit));
            flags.add(flag);
        }

        String status = flags.isEmpty() ? service.TrangThaiDonHang.APPROVED : service.TrangThaiDonHang.PENDING_APPROVAL;
        StringBuilder reason = new StringBuilder(who);
        if (flags.isEmpty()) reason.append(". Tự động duyệt: không có vi phạm cần duyệt");
        else {
            reason.append(". Cần duyệt:");
            for (var f : flags) reason.append("\n• ").append(f.get("message"));
        }
        service.TrangThaiDonHang.setContext(c, actor, reason.toString());
        beforeApprove(c, id, status);
        TruyVanDB.update(c, "UPDATE orders SET status=?, approval_flags=?::jsonb WHERE id=?",
            status, util.XuLyJson.stringify(flags), id);
        return status;
    }

    /**
     * Trong cùng giao dịch, trước khi đơn chuyển trạng thái khi gửi: chốt kho phục vụ của đại lý vào đơn (S4-03)
     * và nếu đơn được duyệt ngay thì giữ chỗ tồn (thiếu tồn thì cả giao dịch huỷ, đơn vẫn là nháp).
     */
    static void beforeApprove(Connection c, long id, String status) throws SQLException {
        TruyVanDB.update(c, "UPDATE orders o SET warehouse_id=k.warehouse_id FROM customers k WHERE k.id=o.agent_id AND o.id=?", id);
        if (service.TrangThaiDonHang.APPROVED.equals(status)) GiuChoTonDB.reserve(c, id);
    }

    /**
     * S4-10: nhân viên phụ trách xác nhận đơn đại lý tự đặt, trong một giao dịch. Vượt hạn mức thì chuyển Chờ duyệt
     * (OVER_CREDIT); không vi phạm thì tự duyệt và giữ chỗ tồn. Đại lý có nợ quá hạn bị trigger chặn.
     */
    public String confirmPortalOrder(long id, long userId) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                var row = TruyVanDB.one(c, "SELECT o.code, o.status, o.payable, o.agent_id FROM orders o JOIN customers k " +
                    "ON k.id=o.agent_id WHERE o.id=? AND k.sales_rep_id=? FOR UPDATE OF o", id, userId);
                if (row == null) throw new NoSuchElementException("Không tìm thấy đơn hàng");
                if (!service.TrangThaiDonHang.PENDING_CONFIRM.equals(row.get("status")))
                    throw new IllegalArgumentException("Đơn " + row.get("code") + " không ở trạng thái Chờ nhân viên xác nhận");
                var credit = TruyVanDB.one(c, "SELECT outstanding, credit_limit FROM customer_credit_status WHERE customer_id=?",
                    row.get("agent_id"));
                BigDecimal payable = (BigDecimal) row.get("payable");
                BigDecimal outstanding = (BigDecimal) credit.get("outstanding"), limit = (BigDecimal) credit.get("credit_limit");
                BigDecimal after = outstanding.add(payable);
                List<Map<String, Object>> flags = new ArrayList<>();
                if (payable.signum() > 0 && after.compareTo(limit) > 0) {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("code", "OVER_CREDIT");
                    f.put("message", limit.signum() == 0
                        ? "Đại lý không được nợ (hạn mức 0 đ): đơn " + service.TaoDonHangService.tien(payable) + " cần duyệt"
                        : "Vượt hạn mức công nợ " + service.TaoDonHangService.tien(after.subtract(limit)) + ": công nợ hiện tại " +
                          service.TaoDonHangService.tien(outstanding) + " + đơn này " + service.TaoDonHangService.tien(payable) +
                          " = " + service.TaoDonHangService.tien(after) + ", hạn mức " + service.TaoDonHangService.tien(limit));
                    flags.add(f);
                }
                String status = submit(c, id, userId, flags, "Nhân viên xác nhận đơn đại lý tự đặt");
                c.commit();
                return status;
            } catch (SQLException e) {
                c.rollback();
                throw service.TrangThaiDonHang.rethrow(e);
            } catch (RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Trạng thái được tính là "đã mua": đơn đã duyệt trở đi, không tính nháp, chờ duyệt, từ chối, huỷ. */
    static final String PURCHASED = "('APPROVED','PICKING','SHIPPED','DELIVERED','CLOSED')";

    /**
     * Lịch sử mua 3 tháng gần nhất của đại lý (S4-04): mỗi sản phẩm có số đơn, tổng và bình quân số lượng mỗi đơn
     * (đơn vị cơ bản), lần mua gần nhất; kèm đơn mua gần nhất để thêm nhanh cả nhóm hàng. priced cho biết sản phẩm
     * còn giá trong bảng giá hiện hành hay không.
     */
    public Map<String, Object> purchaseHistory(long customerId, Long priceListId) throws SQLException {
        var items = query(
            "SELECT p.id AS \"productId\", p.sku, p.name, p.base_unit AS \"baseUnit\", p.active, " +
            "count(DISTINCT o.id) AS orders, trim_scale(sum(l.base_quantity)) AS \"totalQuantity\", " +
            "trim_scale(round(sum(l.base_quantity) / count(DISTINCT o.id), 2)) AS \"averageQuantity\", " +
            DonHangDB.iso("max(o.created_at)") + " AS \"lastOrderedAt\", " +
            "EXISTS (SELECT 1 FROM price_list_lines pll WHERE pll.product_id=p.id AND pll.price_list_id=?) AS priced " +
            "FROM orders o JOIN order_lines l ON l.order_id=o.id JOIN products p ON p.id=l.product_id " +
            "WHERE o.agent_id=? AND o.status IN " + PURCHASED + " AND o.created_at >= CURRENT_DATE - INTERVAL '3 months' " +
            "GROUP BY p.id ORDER BY count(DISTINCT o.id) DESC, sum(l.base_quantity) DESC, p.sku LIMIT 50",
            priceListId, customerId);
        attachUnits(items, "productId");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("months", 3);
        out.put("items", items);
        var last = query("SELECT o.id, o.code, " + DonHangDB.iso("o.created_at") + " AS \"createdAt\" FROM orders o " +
            "WHERE o.agent_id=? AND o.status IN " + PURCHASED + " ORDER BY o.created_at DESC, o.id DESC LIMIT 1", customerId);
        if (last.isEmpty()) out.put("lastOrder", null);
        else {
            Map<String, Object> order = new LinkedHashMap<>(last.get(0));
            var lines = query(
                "SELECT l.product_id AS \"productId\", p.sku, p.name, p.base_unit AS \"baseUnit\", l.unit_id AS \"unitId\", " +
                "trim_scale(l.quantity) AS quantity, (p.active AND u.active AND EXISTS (SELECT 1 FROM price_list_lines pll " +
                "WHERE pll.product_id=p.id AND pll.price_list_id=?)) AS available " +
                "FROM order_lines l JOIN products p ON p.id=l.product_id JOIN product_units u ON u.id=l.unit_id " +
                "WHERE l.order_id=? ORDER BY l.id", priceListId, order.get("id"));
            attachUnits(lines, "productId");
            order.put("lines", lines);
            out.put("lastOrder", order);
        }
        return out;
    }

    /**
     * Đơn nguồn để sao chép (S4-09): đơn bất kỳ trạng thái của đại lý do nhân viên này phụ trách (nháp thì phải là
     * của chính người đó). Điểm giao chỉ giữ nếu còn đang dùng. Null nếu không có hoặc ngoài phạm vi.
     */
    public Map<String, Object> copySource(long id, long userId) throws SQLException {
        var rows = query(
            "SELECT o.id, o.code, o.agent_id AS \"customerId\", o.note, " +
            "(SELECT a.id FROM customer_addresses a WHERE a.id=o.delivery_address_id AND a.customer_id=o.agent_id AND a.active) " +
            "AS \"deliveryPointId\" FROM orders o JOIN customers k ON k.id=o.agent_id " +
            "WHERE o.id=? AND k.sales_rep_id=? AND (o.status<>'DRAFT' OR o.created_by=?)", id, userId, userId);
        if (rows.isEmpty()) return null;
        Map<String, Object> order = new LinkedHashMap<>(rows.get(0));
        order.put("lines", query(
            "SELECT l.product_id AS \"productId\", l.unit_id AS \"unitId\", trim_scale(l.quantity) AS quantity, " +
            "p.sku, p.name, p.active AS \"productActive\" FROM order_lines l JOIN products p ON p.id=l.product_id " +
            "WHERE l.order_id=? ORDER BY l.id", id));
        return order;
    }

    /**
     * S6-05: phần còn nợ của đơn đã xuất. Số lượng thiếu tính theo đơn vị cơ sở; nếu không chia hết cho đơn vị
     * đã đặt thì chuyển sang đơn vị cơ sở của sản phẩm (đơn vị có hệ số 1).
     */
    public Map<String, Object> backorderSource(long id, long userId) throws SQLException {
        var rows = query(
            "SELECT o.id, o.code, o.agent_id AS \"customerId\", " +
            "(SELECT a.id FROM customer_addresses a WHERE a.id=o.delivery_address_id AND a.customer_id=o.agent_id AND a.active) AS \"deliveryPointId\", " +
            "(SELECT b.code FROM orders b WHERE b.backorder_of=o.id AND b.status<>'CANCELLED' LIMIT 1) AS \"backorderCode\" " +
            "FROM orders o JOIN customers k ON k.id=o.agent_id " +
            "WHERE o.id=? AND k.sales_rep_id=? AND o.status IN ('SHIPPED','DELIVERED','CLOSED')", id, userId);
        if (rows.isEmpty()) return null;
        Map<String, Object> order = new LinkedHashMap<>(rows.get(0));
        order.put("lines", query(
            "SELECT s.product_id AS \"productId\", " +
            "CASE WHEN mod(s.short_base, s.conversion_factor)=0 THEN s.unit_id ELSE " +
            "(SELECT u.id FROM product_units u WHERE u.product_id=s.product_id AND u.factor=1 AND u.active ORDER BY u.id LIMIT 1) END AS \"unitId\", " +
            "trim_scale(CASE WHEN mod(s.short_base, s.conversion_factor)=0 THEN s.short_base/s.conversion_factor ELSE s.short_base END) AS quantity, " +
            "p.sku, p.name, p.active AS \"productActive\" FROM (SELECT l.id, l.product_id, l.unit_id, l.conversion_factor, " +
            "l.base_quantity-l.delivered_base_quantity AS short_base FROM order_lines l WHERE l.order_id=? AND l.delivered_base_quantity IS NOT NULL) s " +
            "JOIN products p ON p.id=s.product_id WHERE s.short_base>0 ORDER BY s.id", id));
        return order;
    }

    /** Gắn đơn nháp vừa tạo làm đơn giao bù của đơn gốc; chỉ áp dụng cho nháp của chính người tạo. */
    public void markBackorder(long draftId, long sourceId, long userId) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            if (TruyVanDB.update(c, "UPDATE orders SET backorder_of=? WHERE id=? AND created_by=? AND status='DRAFT'", sourceId, draftId, userId) != 1)
                throw new IllegalArgumentException("Không gắn được đơn giao bù");
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) throw new IllegalArgumentException("Đơn gốc đã có đơn giao bù khác");
            throw e;
        }
    }

    /** Mở lại một đơn nháp của chính người dùng (kèm dòng hàng và các đơn vị tính để sửa tiếp). */
    public Map<String, Object> load(long id, long userId) throws SQLException {
        var rows = query(
            "SELECT o.id, o.code, o.status, o.agent_id AS \"customerId\", u.name AS \"customerName\", " +
            "COALESCE(u.phone,'') AS \"customerPhone\", o.delivery_address AS \"deliveryAddress\", o.delivery_address_id AS \"deliveryPointId\", " +
            "u.trading_locked AS \"tradingLocked\", COALESCE(u.lock_reason,'') AS \"lockReason\", " +
            "o.desired_delivery_date AS \"desiredDate\", o.note, o.subtotal, o.discount_total AS discount, o.payable, " +
            "o.approval_flags::text AS \"approvalFlagsJson\" " +
            "FROM orders o LEFT JOIN customers u ON u.id=o.agent_id WHERE o.id=? AND o.created_by=? AND u.sales_rep_id=?", id, userId, userId);
        if (rows.isEmpty()) return null;
        Map<String, Object> order = new LinkedHashMap<>(rows.get(0));
        order.put("statusLabel", service.TrangThaiDonHang.label(order.get("status")));
        order.put("approvalFlags", util.XuLyJson.parse(Objects.toString(order.remove("approvalFlagsJson"), "[]")));
        // S4-05: nháp bị quản lý trả lại → ý kiến gần nhất để nhân viên biết cần sửa gì.
        var review = query("SELECT comment, decided_by_name AS \"decidedByName\", " + DonHangDB.iso("decided_at") +
            " AS \"decidedAt\" FROM order_approvals WHERE order_id=? AND action='RETURN' ORDER BY decided_at DESC, id DESC LIMIT 1", id);
        order.put("lastReturn", "DRAFT".equals(order.get("status")) && !review.isEmpty() ? review.get(0) : null);
        // Giá sửa thủ công được trả lại để mở nháp vẫn giữ nguyên; giá bảng giá thì để máy chủ tính lại.
        var lines = query(
            "SELECT l.product_id AS \"productId\", p.sku, p.name, l.unit_id AS \"unitId\", trim_scale(l.quantity) AS quantity, " +
            "CASE WHEN l.price_overridden THEN round(l.base_price*l.conversion_factor,2) END AS \"unitPrice\" " +
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
