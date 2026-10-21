package service;

import db.StockLedgerDB;
import db.TruyVanDB;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import security.PhanQuyen;
import util.KetNoiDB;

/**
 * S8-01: phiếu trả hàng từ đại lý theo hoá đơn gốc.
 * Nhân viên kinh doanh (đại lý mình phụ trách) hoặc quản lý kinh doanh lập phiếu; Quản lý kho xác nhận nhận hàng
 * thì mới cộng tồn: hàng tốt về đúng lô/vị trí đã xuất, hàng lỗi vào kho hàng lỗi cùng số lô và hạn dùng.
 */
public final class TraHangService {
    static final Set<String> REASONS = Set.of("DAMAGED", "EXPIRED", "WRONG_ITEM", "QUALITY", "CUSTOMER_REQUEST", "OTHER");
    private static final int MAX_LINES = 200;

    static boolean seesAll(Collection<String> roles) {
        return PhanQuyen.allows(roles, "orders.read") || PhanQuyen.allows(roles, "invoices.write")
            || PhanQuyen.allows(roles, "warehouses.manage");
    }
    /** Nhân viên kinh doanh (đại lý mình phụ trách) hoặc quản lý kinh doanh (duyệt đơn) lập phiếu trả. */
    static boolean canCreate(Collection<String> roles) {
        return PhanQuyen.allows(roles, "orders.approve") || PhanQuyen.allows(roles, "orders.own");
    }
    static boolean canReceive(Collection<String> roles) {
        return PhanQuyen.allows(roles, "warehouses.manage");
    }

    /** Điều kiện SQL giới hạn đại lý theo người dùng; quản lý/kế toán/kho thấy toàn bộ. */
    private static String scope(Collection<String> roles, String customerColumn) {
        return seesAll(roles) ? "" :
            " AND EXISTS(SELECT 1 FROM customers sc WHERE sc.id=" + customerColumn + " AND sc.sales_rep_id=?)";
    }
    private static Object[] scopeArgs(Collection<String> roles, long user, Object... before) {
        List<Object> args = new ArrayList<>(Arrays.asList(before));
        if (!seesAll(roles)) args.add(user);
        return args.toArray();
    }

    public Map<String, Object> list(long user, Collection<String> roles) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            var rows = TruyVanDB.rows(c,
                "SELECT r.id,r.code,r.status,r.total_amount,r.note,r.created_at::text created_at,r.decided_at::text decided_at," +
                "i.invoice_number,k.code customer_code,k.name customer_name,w.name warehouse_name,u.full_name created_by_name," +
                "(SELECT count(*) FROM sales_return_lines l WHERE l.return_id=r.id) line_count " +
                "FROM sales_returns r JOIN invoices i ON i.id=r.invoice_id JOIN customers k ON k.id=r.customer_id " +
                "JOIN warehouses w ON w.id=r.warehouse_id JOIN users u ON u.id=r.created_by WHERE TRUE" +
                scope(roles, "r.customer_id") + " ORDER BY r.id DESC LIMIT 300", scopeArgs(roles, user));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("returns", rows);
            out.put("canCreate", canCreate(roles));
            out.put("canReceive", canReceive(roles));
            out.put("defectWarehouses", canReceive(roles)
                ? TruyVanDB.rows(c, "SELECT id,code,name FROM warehouses WHERE active AND is_defect ORDER BY code") : List.of());
            out.put("reasons", List.of(
                Map.of("code", "DAMAGED", "label", "Hàng hỏng, vỡ"),
                Map.of("code", "EXPIRED", "label", "Cận hoặc hết hạn"),
                Map.of("code", "WRONG_ITEM", "label", "Giao nhầm hàng"),
                Map.of("code", "QUALITY", "label", "Lỗi chất lượng"),
                Map.of("code", "CUSTOMER_REQUEST", "label", "Đại lý trả theo thoả thuận"),
                Map.of("code", "OTHER", "label", "Lý do khác")));
            return out;
        }
    }

    /** Hoá đơn có thể lập phiếu trả, trong phạm vi đại lý của người dùng. */
    public List<Map<String, Object>> invoices(long user, Collection<String> roles, String keyword) throws SQLException {
        if (!canCreate(roles)) throw new SecurityException("Bạn không có quyền lập phiếu trả hàng");
        String q = KhoService.text(keyword, "Từ khoá", 100, false);
        try (Connection c = KetNoiDB.getConnection()) {
            return TruyVanDB.rows(c,
                "SELECT i.id,i.invoice_number,i.issued_at::date::text issued_date,i.total_amount,k.code customer_code,k.name customer_name " +
                "FROM invoices i JOIN customers k ON k.id=i.customer_id WHERE (?='' OR i.invoice_number ILIKE '%'||?||'%' OR k.code ILIKE '%'||?||'%' OR k.name ILIKE '%'||?||'%')" +
                scope(roles, "i.customer_id") + " ORDER BY i.issued_at DESC,i.id DESC LIMIT 100", scopeArgs(roles, user, q, q, q, q));
        }
    }

    /** Dòng hoá đơn kèm số lượng còn được trả (đã giao trừ đã trả và đang chờ nhận). */
    public Map<String, Object> invoice(long invoiceId, long user, Collection<String> roles) throws SQLException {
        if (!canCreate(roles)) throw new SecurityException("Bạn không có quyền lập phiếu trả hàng");
        try (Connection c = KetNoiDB.getConnection()) {
            var head = TruyVanDB.one(c,
                "SELECT i.id,i.invoice_number,i.issued_at::date::text issued_date,i.customer_id,k.code customer_code,k.name customer_name " +
                "FROM invoices i JOIN customers k ON k.id=i.customer_id WHERE i.id=?" + scope(roles, "i.customer_id"), scopeArgs(roles, user, invoiceId));
            if (head == null) throw new NoSuchElementException("Không tìm thấy hoá đơn");
            head.put("lines", invoiceLines(c, invoiceId));
            return head;
        }
    }

    private static List<Map<String, Object>> invoiceLines(Connection c, long invoiceId) throws SQLException {
        return TruyVanDB.rows(c,
            "SELECT il.id,il.order_line_id,o.code order_code,p.sku,p.name product_name,p.lot_managed,u.name unit_name,ol.conversion_factor," +
            "il.delivered_quantity,round(il.line_total/il.delivered_quantity,2) unit_price,il.line_total," +
            "COALESCE((SELECT sum(l.quantity) FROM sales_return_lines l JOIN sales_returns r ON r.id=l.return_id " +
            " WHERE l.invoice_line_id=il.id AND r.status IN('PENDING','RECEIVED')),0) returned_quantity " +
            "FROM invoice_lines il JOIN order_lines ol ON ol.id=il.order_line_id JOIN orders o ON o.id=il.order_id " +
            "JOIN products p ON p.id=il.product_id JOIN product_units u ON u.id=il.unit_id WHERE il.invoice_id=? ORDER BY il.id", invoiceId);
    }

    public Map<String, Object> detail(long id, long user, Collection<String> roles) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            var head = TruyVanDB.one(c,
                "SELECT r.*,r.created_at::text created_at_text,r.decided_at::text decided_at_text,i.invoice_number,k.code customer_code,k.name customer_name," +
                "w.name warehouse_name,dw.name defect_warehouse_name,u.full_name created_by_name,d.full_name decided_by_name " +
                "FROM sales_returns r JOIN invoices i ON i.id=r.invoice_id JOIN customers k ON k.id=r.customer_id JOIN warehouses w ON w.id=r.warehouse_id " +
                "LEFT JOIN warehouses dw ON dw.id=r.defect_warehouse_id JOIN users u ON u.id=r.created_by LEFT JOIN users d ON d.id=r.decided_by " +
                "WHERE r.id=?" + scope(roles, "r.customer_id"), scopeArgs(roles, user, id));
            if (head == null) throw new NoSuchElementException("Không tìm thấy phiếu trả hàng");
            var lines = TruyVanDB.rows(c,
                "SELECT l.*,p.sku,p.name product_name,p.lot_managed,u.name unit_name FROM sales_return_lines l JOIN products p ON p.id=l.product_id " +
                "JOIN product_units u ON u.id=l.unit_id WHERE l.return_id=? ORDER BY l.id", id);
            for (var line : lines) {
                long lineId = TruyVanDB.id(line);
                if ("PENDING".equals(head.get("status"))) line.put("lots", shippedLots(c, ((Number) line.get("order_line_id")).longValue()));
                line.put("allocations", TruyVanDB.rows(c,
                    "SELECT a.base_quantity,w.name warehouse_name,b.lot_number,b.expiry_date::text expiry_date,COALESCE(loc.name,'Chưa phân vị trí') location_name " +
                    "FROM sales_return_allocations a JOIN stock_bins b ON b.id=a.target_bin_id JOIN warehouses w ON w.id=a.warehouse_id " +
                    "LEFT JOIN warehouse_locations loc ON loc.id=b.location_id WHERE a.return_line_id=? ORDER BY a.id", lineId));
            }
            head.put("lines", lines);
            head.put("canReceive", canReceive(roles) && "PENDING".equals(head.get("status")));
            head.put("canCancel", "PENDING".equals(head.get("status")) && canCreate(roles)
                && (PhanQuyen.allows(roles, "orders.approve") || Objects.equals(((Number) head.get("created_by")).longValue(), user)));
            return head;
        }
    }

    /** Lô/vị trí đã xuất cho một dòng đơn và phần còn có thể nhận trả về lô đó (đơn vị cơ sở). */
    private static List<Map<String, Object>> shippedLots(Connection c, long orderLineId) throws SQLException {
        return TruyVanDB.rows(c,
            "SELECT m.bin_id,b.lot_number,b.expiry_date::text expiry_date,COALESCE(loc.name,'Chưa phân vị trí') location_name," +
            "-sum(m.delta) shipped,COALESCE((SELECT sum(a.base_quantity) FROM sales_return_allocations a WHERE a.source_bin_id=m.bin_id " +
            " AND a.return_line_id IN (SELECT id FROM sales_return_lines WHERE order_line_id=?)),0) returned " +
            "FROM stock_movements m JOIN stock_bins b ON b.id=m.bin_id LEFT JOIN warehouse_locations loc ON loc.id=b.location_id " +
            "WHERE m.kind IN('SHIPMENT_OUT','DELIVERY_RETURN') AND m.line_id=? GROUP BY m.bin_id,b.lot_number,b.expiry_date,loc.name " +
            "HAVING -sum(m.delta)>0 " +
            "ORDER BY b.expiry_date DESC NULLS FIRST,m.bin_id DESC", orderLineId, orderLineId);
    }

    public long create(long user, Collection<String> roles, Map<String, Object> in) throws SQLException {
        if (!canCreate(roles)) throw new SecurityException("Bạn không có quyền lập phiếu trả hàng");
        long invoiceId = KhoService.id(in.get("invoiceId"));
        String note = KhoService.text(in.get("note"), "Ghi chú", 1000, false);
        if (!(in.get("lines") instanceof List<?> raw) || raw.isEmpty() || raw.size() > MAX_LINES)
            throw new IllegalArgumentException("Chọn ít nhất một dòng hàng trả (tối đa " + MAX_LINES + " dòng)");
        Map<Long, Map<?, ?>> requested = new LinkedHashMap<>();
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?> line)) throw new IllegalArgumentException("Dòng hàng trả không hợp lệ");
            if (requested.put(KhoService.id(line.get("invoiceLineId")), line) != null) throw new IllegalArgumentException("Dòng hoá đơn bị chọn trùng");
        }
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                KhoService.actor(c, user);
                var invoice = TruyVanDB.one(c, "SELECT id,customer_id,invoice_number FROM invoices WHERE id=?" + scope(roles, "customer_id"),
                    scopeArgs(roles, user, invoiceId));
                if (invoice == null) throw new NoSuchElementException("Không tìm thấy hoá đơn");
                long customer = ((Number) invoice.get("customer_id")).longValue();
                ThanhToanService.lockCustomer(c, customer);
                Map<Long, Map<String, Object>> lines = new HashMap<>();
                for (var l : invoiceLines(c, invoiceId)) lines.put(TruyVanDB.id(l), l);
                Long warehouse = null;
                List<Object[]> inserts = new ArrayList<>();
                BigDecimal total = BigDecimal.ZERO;
                for (var entry : requested.entrySet()) {
                    var line = lines.get(entry.getKey());
                    if (line == null) throw new IllegalArgumentException("Dòng hàng không thuộc hoá đơn " + invoice.get("invoice_number"));
                    Map<?, ?> item = entry.getValue();
                    BigDecimal qty = PhieuNhapKhoService.quantity(item.get("quantity"), false);
                    String reason = KhoService.text(item.get("reason"), "Lý do trả", 20, true);
                    if (!REASONS.contains(reason)) throw new IllegalArgumentException("Lý do trả không hợp lệ");
                    String reasonNote = KhoService.text(item.get("reasonNote"), "Ghi chú lý do", 500, reason.equals("OTHER"));
                    BigDecimal delivered = (BigDecimal) line.get("delivered_quantity"), returned = (BigDecimal) line.get("returned_quantity");
                    BigDecimal remaining = delivered.subtract(returned);
                    if (qty.compareTo(remaining) > 0)
                        throw new IllegalArgumentException("Dòng " + line.get("sku") + " chỉ còn được trả tối đa " + remaining.stripTrailingZeros().toPlainString() + " " + line.get("unit_name"));
                    BigDecimal factor = (BigDecimal) line.get("conversion_factor");
                    BigDecimal base = qty.multiply(factor);
                    if (base.stripTrailingZeros().scale() > 0) throw new IllegalArgumentException("Số lượng trả quy đổi phải là số nguyên đơn vị cơ sở");
                    BigDecimal lineTotal = (BigDecimal) line.get("line_total");
                    // Trả hết phần còn lại thì lấy đúng phần tiền còn lại, tránh lệch làm tròn giữa các lần trả.
                    BigDecimal amount = qty.compareTo(remaining) == 0
                        ? lineTotal.subtract(returnedAmount(c, entry.getKey()))
                        : lineTotal.multiply(qty).divide(delivered, 2, RoundingMode.HALF_UP);
                    long orderLine = ((Number) line.get("order_line_id")).longValue();
                    var shipped = TruyVanDB.one(c, "SELECT o.warehouse_id FROM order_lines ol JOIN orders o ON o.id=ol.order_id WHERE ol.id=?", orderLine);
                    long wh = ((Number) shipped.get("warehouse_id")).longValue();
                    if (warehouse == null) warehouse = wh;
                    else if (warehouse != wh) throw new IllegalArgumentException("Các dòng trả phải cùng kho đã xuất; hãy lập phiếu riêng cho từng kho");
                    var product = TruyVanDB.one(c, "SELECT product_id,unit_id FROM invoice_lines WHERE id=?", entry.getKey());
                    inserts.add(new Object[]{entry.getKey(), orderLine, product.get("product_id"), product.get("unit_id"), qty, factor, base,
                        line.get("unit_price"), amount, reason, reasonNote});
                    total = total.add(amount);
                }
                long serial = ((Number) TruyVanDB.one(c, "UPDATE invoice_number_counters SET last_number=last_number+1 WHERE document_type='RETURN' RETURNING last_number").get("last_number")).longValue();
                long id = TruyVanDB.id(TruyVanDB.one(c,
                    "INSERT INTO sales_returns(code,invoice_id,customer_id,warehouse_id,note,total_amount,created_by) VALUES(?,?,?,?,?,?,?) RETURNING id",
                    String.format("TH-%06d", serial), invoiceId, customer, warehouse, note, total, user));
                for (Object[] v : inserts)
                    TruyVanDB.update(c, "INSERT INTO sales_return_lines(return_id,invoice_line_id,order_line_id,product_id,unit_id,quantity,conversion_factor,base_quantity,unit_price,amount,reason,reason_note) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        prepend(id, v));
                c.commit();
                return id;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }

    private static BigDecimal returnedAmount(Connection c, long invoiceLine) throws SQLException {
        return (BigDecimal) TruyVanDB.one(c, "SELECT COALESCE(sum(l.amount),0) amount FROM sales_return_lines l JOIN sales_returns r ON r.id=l.return_id " +
            "WHERE l.invoice_line_id=? AND r.status IN('PENDING','RECEIVED')", invoiceLine).get("amount");
    }

    /** Quản lý kho xác nhận nhận hàng: kiểm tra từng dòng (hàng tốt/hàng lỗi), cộng tồn và ghi thẻ kho. */
    public void receive(long id, long user, Collection<String> roles, Map<String, Object> in) throws SQLException {
        if (!canReceive(roles)) throw new SecurityException("Chỉ Quản lý kho được xác nhận nhận hàng trả");
        String note = KhoService.text(in.get("note"), "Ghi chú nhận hàng", 1000, false);
        Map<Long, Map<?, ?>> decisions = new HashMap<>();
        if (in.get("lines") instanceof List<?> raw) for (Object item : raw) {
            if (!(item instanceof Map<?, ?> m)) throw new IllegalArgumentException("Dòng nhận hàng không hợp lệ");
            if (decisions.put(KhoService.id(m.get("lineId")), m) != null) throw new IllegalArgumentException("Dòng nhận hàng bị trùng");
        }
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                KhoService.actor(c, user);
                var head = TruyVanDB.one(c, "SELECT * FROM sales_returns WHERE id=? FOR UPDATE", id);
                if (head == null) throw new NoSuchElementException("Không tìm thấy phiếu trả hàng");
                if (!"PENDING".equals(head.get("status"))) throw new IllegalArgumentException("Phiếu trả hàng đã được xử lý");
                long warehouse = ((Number) head.get("warehouse_id")).longValue();
                KhoService.access(c, warehouse, user, roles);
                var lines = TruyVanDB.rows(c, "SELECT * FROM sales_return_lines WHERE return_id=? ORDER BY id FOR UPDATE", id);
                if (decisions.size() != lines.size() || !decisions.keySet().containsAll(lines.stream().map(TruyVanDB::id).toList()))
                    throw new IllegalArgumentException("Phải kiểm tra đủ tất cả dòng của phiếu trả");
                Long defectWarehouse = null;
                for (var line : lines) {
                    String condition = KhoService.text(decisions.get(TruyVanDB.id(line)).get("condition"), "Tình trạng hàng", 10, true);
                    if (!Set.of("GOOD", "DEFECT").contains(condition)) throw new IllegalArgumentException("Tình trạng hàng không hợp lệ");
                    if (condition.equals("DEFECT") && defectWarehouse == null) {
                        if (in.get("defectWarehouseId") == null) throw new IllegalArgumentException("Chọn kho hàng lỗi cho các dòng hàng lỗi");
                        defectWarehouse = KhoService.id(in.get("defectWarehouseId"));
                        if (TruyVanDB.one(c, "SELECT id FROM warehouses WHERE id=? AND active AND is_defect FOR SHARE", defectWarehouse) == null)
                            throw new IllegalArgumentException("Chọn kho hàng lỗi đang hoạt động cho hàng hỏng");
                        KhoService.access(c, defectWarehouse, user, roles);
                    }
                }
                String reason = "Nhận hàng trả " + head.get("code") + (note.isEmpty() ? "" : ": " + note);
                for (var line : lines) {
                    long lineId = TruyVanDB.id(line), product = ((Number) line.get("product_id")).longValue();
                    Map<?, ?> decision = decisions.get(lineId);
                    String condition = String.valueOf(decision.get("condition"));
                    TruyVanDB.update(c, "UPDATE sales_return_lines SET condition=? WHERE id=?", condition, lineId);
                    // Các phiếu trả khác nhau có thể cùng nhận lại một dòng/lô đã xuất.
                    // Khóa trước khi đọc phần còn được trả để không phân bổ vượt lô khi nhận đồng thời.
                    StockLedgerDB.lockStock(c, warehouse, product);
                    var lots = shippedLots(c, ((Number) line.get("order_line_id")).longValue());
                    Map<Long, BigDecimal> plan = allocation(lots, (BigDecimal) line.get("base_quantity"), decision.get("allocations"));
                    long targetWarehouse = condition.equals("GOOD") ? warehouse : defectWarehouse;
                    Map<Long, BigDecimal> targetQuantities = new LinkedHashMap<>();
                    for (var lot : lots) {
                        long sourceBin = ((Number) lot.get("bin_id")).longValue();
                        BigDecimal qty = plan.getOrDefault(sourceBin, BigDecimal.ZERO);
                        if (qty.signum() == 0) continue;
                        long targetBin = sourceBin;
                        if (condition.equals("DEFECT")) {
                            var src = TruyVanDB.one(c, "SELECT lot_number,expiry_date FROM stock_bins WHERE id=?", sourceBin);
                            StockLedgerDB.lockStock(c, targetWarehouse, product);
                            targetBin = StockLedgerDB.bin(c, targetWarehouse, product, null, (String) src.get("lot_number"), src.get("expiry_date"));
                        }
                        targetQuantities.merge(targetBin, qty, BigDecimal::add);
                        TruyVanDB.update(c, "INSERT INTO sales_return_allocations(return_line_id,source_bin_id,target_bin_id,warehouse_id,base_quantity) VALUES(?,?,?,?,?)",
                            lineId, sourceBin, targetBin, targetWarehouse, qty);
                    }
                    // Nhiều vị trí nguồn của cùng lô có thể về chung một vị trí kho lỗi.
                    // Giữ từng phân bổ nguồn nhưng chỉ ghi một bút toán cho mỗi vị trí đích.
                    for (var target : targetQuantities.entrySet())
                        StockLedgerDB.change(c, targetWarehouse, product, target.getKey(), target.getValue(), "SALES_RETURN", id, lineId, user,
                            condition.equals("GOOD") ? reason : reason + " (hàng lỗi)");
                }
                TruyVanDB.update(c, "UPDATE sales_returns SET status='RECEIVED',defect_warehouse_id=?,decided_by=?,decided_at=CURRENT_TIMESTAMP,decision_note=? WHERE id=?",
                    defectWarehouse, user, note, id);
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }

    /**
     * Phân bổ số lượng trả (đơn vị cơ sở) về các lô đã xuất. Thủ kho có thể chỉ định từng lô; nếu không,
     * mặc định lô có hạn dùng xa nhất trước vì hàng đại lý còn giữ thường là lô mới nhận.
     */
    static Map<Long, BigDecimal> allocation(List<Map<String, Object>> lots, BigDecimal need, Object requested) {
        Map<Long, BigDecimal> free = new LinkedHashMap<>();
        for (var lot : lots) free.put(((Number) lot.get("bin_id")).longValue(),
            ((BigDecimal) lot.get("shipped")).subtract((BigDecimal) lot.get("returned")).max(BigDecimal.ZERO));
        Map<Long, BigDecimal> plan = new LinkedHashMap<>();
        if (requested instanceof List<?> raw && !raw.isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Object item : raw) {
                if (!(item instanceof Map<?, ?> m)) throw new IllegalArgumentException("Phân bổ lô không hợp lệ");
                long bin = KhoService.id(m.get("binId"));
                BigDecimal qty = PhieuNhapKhoService.quantity(m.get("quantity"), true);
                if (qty.signum() == 0) continue;
                if (qty.stripTrailingZeros().scale() > 0) throw new IllegalArgumentException("Số lượng theo lô phải là số nguyên đơn vị cơ sở");
                if (!free.containsKey(bin) || qty.compareTo(free.get(bin)) > 0) throw new IllegalArgumentException("Lô chọn không thuộc hàng đã xuất hoặc vượt số đã xuất");
                if (plan.put(bin, qty) != null) throw new IllegalArgumentException("Lô bị chọn trùng");
                sum = sum.add(qty);
            }
            if (sum.compareTo(need) != 0) throw new IllegalArgumentException("Tổng số lượng theo lô phải bằng số lượng trả");
            return plan;
        }
        BigDecimal left = need;
        for (var entry : free.entrySet()) {
            if (left.signum() == 0) break;
            BigDecimal take = left.min(entry.getValue());
            if (take.signum() > 0) { plan.put(entry.getKey(), take); left = left.subtract(take); }
        }
        if (left.signum() > 0) throw new IllegalArgumentException("Số lượng trả vượt số đã xuất theo lô; kiểm tra lại sổ kho");
        return plan;
    }

    public void reject(long id, long user, Collection<String> roles, Map<String, Object> in) throws SQLException {
        if (!canReceive(roles)) throw new SecurityException("Chỉ Quản lý kho được từ chối nhận hàng trả");
        decide(id, user, roles, "REJECTED", KhoService.text(in.get("note"), "Lý do từ chối", 1000, true), false);
    }

    public void cancel(long id, long user, Collection<String> roles, Map<String, Object> in) throws SQLException {
        if (!canCreate(roles)) throw new SecurityException("Bạn không có quyền huỷ phiếu trả hàng");
        decide(id, user, roles, "CANCELLED", KhoService.text(in.get("note"), "Lý do huỷ", 1000, true), true);
    }

    private void decide(long id, long user, Collection<String> roles, String status, String note, boolean creatorOnly) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                KhoService.actor(c, user);
                var head = TruyVanDB.one(c, "SELECT * FROM sales_returns WHERE id=?" + scope(roles, "customer_id") + " FOR UPDATE", scopeArgs(roles, user, id));
                if (head == null) throw new NoSuchElementException("Không tìm thấy phiếu trả hàng");
                if (!"PENDING".equals(head.get("status"))) throw new IllegalArgumentException("Phiếu trả hàng đã được xử lý");
                if (creatorOnly && !PhanQuyen.allows(roles, "orders.approve") && ((Number) head.get("created_by")).longValue() != user)
                    throw new SecurityException("Chỉ người lập phiếu hoặc quản lý kinh doanh được huỷ phiếu");
                if (!creatorOnly) KhoService.access(c, ((Number) head.get("warehouse_id")).longValue(), user, roles);
                TruyVanDB.update(c, "UPDATE sales_returns SET status=?,decided_by=?,decided_at=CURRENT_TIMESTAMP,decision_note=? WHERE id=?", status, user, note, id);
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }

    private static Object[] prepend(Object first, Object[] rest) {
        Object[] out = new Object[rest.length + 1];
        out[0] = first;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }
}
