package service;

import db.StockLedgerDB;
import db.TruyVanDB;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import util.KetNoiDB;

/**
 * S8-03: phiếu điều chỉnh giảm tồn cho hàng hỏng, hàng mất, hàng hết hạn phải huỷ.
 * Nhân viên/Quản lý kho lập phiếu trong kho được phân công, bắt buộc lý do và giá trị tổn thất;
 * chỉ Quản lý kho khác người lập được duyệt. Tồn chỉ giảm khi duyệt và ghi thẻ kho loại ADJUSTMENT.
 */
public final class DieuChinhTonService {
    static final Set<String> REASONS = Set.of("DAMAGED", "LOST", "EXPIRED_DISPOSAL");
    private static final int MAX_LINES = 200;

    private static String scope(Collection<String> roles) {
        return KhoService.manager(roles) ? "" : " AND EXISTS(SELECT 1 FROM user_warehouses a WHERE a.warehouse_id=s.warehouse_id AND a.user_id=?)";
    }
    private static Object[] args(Collection<String> roles, long user, Object... before) {
        List<Object> out = new ArrayList<>(Arrays.asList(before));
        if (!KhoService.manager(roles)) out.add(user);
        return out.toArray();
    }

    public Map<String, Object> list(long user, Collection<String> roles) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("adjustments", TruyVanDB.rows(c,
                "SELECT s.id,s.code,s.reason,s.status,s.total_loss,s.note,s.created_by,s.created_at::text created_at,s.decided_at::text decided_at," +
                "w.code warehouse_code,w.name warehouse_name,u.full_name created_by_name,d.full_name decided_by_name," +
                "(SELECT count(*) FROM stock_adjustment_lines l WHERE l.adjustment_id=s.id) line_count " +
                "FROM stock_adjustments s JOIN warehouses w ON w.id=s.warehouse_id JOIN users u ON u.id=s.created_by LEFT JOIN users d ON d.id=s.decided_by " +
                "WHERE TRUE" + scope(roles) + " ORDER BY s.id DESC LIMIT 300", args(roles, user)));
            out.put("warehouses", TruyVanDB.rows(c, "SELECT w.id,w.code,w.name FROM warehouses w WHERE w.active" +
                (KhoService.manager(roles) ? "" : " AND EXISTS(SELECT 1 FROM user_warehouses a WHERE a.warehouse_id=w.id AND a.user_id=?)") + " ORDER BY w.code",
                KhoService.manager(roles) ? new Object[]{} : new Object[]{user}));
            out.put("canApprove", KhoService.manager(roles));
            out.put("userId", user);
            return out;
        }
    }

    /** Lô/vị trí còn tồn trong kho để chọn dòng điều chỉnh. */
    public List<Map<String, Object>> bins(long warehouse, String keyword, long user, Collection<String> roles) throws SQLException {
        String q = KhoService.text(keyword, "Từ khoá", 100, false);
        try (Connection c = KetNoiDB.getConnection()) {
            KhoService.access(c, warehouse, user, roles);
            return TruyVanDB.rows(c,
                "SELECT b.id bin_id,b.product_id,p.sku,p.name product_name,p.base_unit,b.lot_number,b.expiry_date::text expiry_date," +
                "(b.expiry_date IS NOT NULL AND b.expiry_date<=CURRENT_DATE) expired,COALESCE(l.name,'Chưa phân vị trí') location_name,b.on_hand " +
                "FROM stock_bins b JOIN products p ON p.id=b.product_id LEFT JOIN warehouse_locations l ON l.id=b.location_id " +
                "WHERE b.warehouse_id=? AND b.on_hand>0 AND (?='' OR p.sku ILIKE '%'||?||'%' OR p.name ILIKE '%'||?||'%' OR b.lot_number ILIKE '%'||?||'%') " +
                "ORDER BY p.sku,b.expiry_date NULLS LAST,b.id LIMIT 200", warehouse, q, q, q, q);
        }
    }

    public Map<String, Object> detail(long id, long user, Collection<String> roles) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            var head = TruyVanDB.one(c,
                "SELECT s.*,s.created_at::text created_at_text,s.decided_at::text decided_at_text,w.code warehouse_code,w.name warehouse_name," +
                "u.full_name created_by_name,d.full_name decided_by_name FROM stock_adjustments s JOIN warehouses w ON w.id=s.warehouse_id " +
                "JOIN users u ON u.id=s.created_by LEFT JOIN users d ON d.id=s.decided_by WHERE s.id=?" + scope(roles), args(roles, user, id));
            if (head == null) throw new NoSuchElementException("Không tìm thấy phiếu điều chỉnh tồn");
            head.put("lines", TruyVanDB.rows(c,
                "SELECT l.*,p.sku,p.name product_name,p.base_unit,b.lot_number,b.expiry_date::text expiry_date,b.on_hand current_on_hand," +
                "COALESCE(loc.name,'Chưa phân vị trí') location_name FROM stock_adjustment_lines l JOIN products p ON p.id=l.product_id " +
                "JOIN stock_bins b ON b.id=l.bin_id LEFT JOIN warehouse_locations loc ON loc.id=b.location_id WHERE l.adjustment_id=? ORDER BY l.id", id));
            boolean pending = "PENDING".equals(head.get("status"));
            boolean own = ((Number) head.get("created_by")).longValue() == user;
            head.put("canApprove", pending && KhoService.manager(roles) && !own);
            head.put("canCancel", pending && own);
            return head;
        }
    }

    public long create(long user, Collection<String> roles, Map<String, Object> in) throws SQLException {
        long warehouse = KhoService.id(in.get("warehouseId"));
        String reason = KhoService.text(in.get("reason"), "Lý do điều chỉnh", 20, true);
        if (!REASONS.contains(reason)) throw new IllegalArgumentException("Lý do điều chỉnh không hợp lệ");
        String note = KhoService.text(in.get("note"), "Diễn giải", 1000, reason.equals("LOST"));
        if (!(in.get("lines") instanceof List<?> raw) || raw.isEmpty() || raw.size() > MAX_LINES)
            throw new IllegalArgumentException("Chọn từ 1 đến " + MAX_LINES + " dòng hàng cần điều chỉnh");
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                KhoService.actor(c, user);
                KhoService.access(c, warehouse, user, roles);
                List<Object[]> lines = new ArrayList<>();
                Set<Long> seen = new HashSet<>();
                BigDecimal total = BigDecimal.ZERO;
                for (Object item : raw) {
                    if (!(item instanceof Map<?, ?> m)) throw new IllegalArgumentException("Dòng điều chỉnh không hợp lệ");
                    long bin = KhoService.id(m.get("binId"));
                    if (!seen.add(bin)) throw new IllegalArgumentException("Một lô/vị trí bị chọn trùng");
                    var b = TruyVanDB.one(c, "SELECT b.product_id,b.on_hand,b.lot_number,(b.expiry_date IS NOT NULL AND b.expiry_date<=CURRENT_DATE) expired,p.sku FROM stock_bins b JOIN products p ON p.id=b.product_id WHERE b.id=? AND b.warehouse_id=?", bin, warehouse);
                    if (b == null) throw new IllegalArgumentException("Lô/vị trí không thuộc kho đã chọn");
                    BigDecimal qty = PhieuNhapKhoService.quantity(m.get("quantity"), false);
                    if (qty.compareTo((BigDecimal) b.get("on_hand")) > 0)
                        throw new IllegalArgumentException("Dòng " + b.get("sku") + " lô " + b.get("lot_number") + " chỉ còn " + ((BigDecimal) b.get("on_hand")).stripTrailingZeros().toPlainString());
                    if (reason.equals("EXPIRED_DISPOSAL") && !Boolean.TRUE.equals(b.get("expired")))
                        throw new IllegalArgumentException("Dòng " + b.get("sku") + " lô " + b.get("lot_number") + " chưa hết hạn, không huỷ theo lý do hết hạn");
                    BigDecimal loss = money(m.get("lossValue"));
                    String lineNote = KhoService.text(m.get("note"), "Ghi chú dòng", 500, false);
                    lines.add(new Object[]{b.get("product_id"), bin, qty, loss, lineNote});
                    total = total.add(loss);
                }
                long serial = ((Number) TruyVanDB.one(c, "UPDATE invoice_number_counters SET last_number=last_number+1 WHERE document_type='STOCK_ADJ' RETURNING last_number").get("last_number")).longValue();
                long id = TruyVanDB.id(TruyVanDB.one(c,
                    "INSERT INTO stock_adjustments(code,warehouse_id,reason,note,total_loss,created_by) VALUES(?,?,?,?,?,?) RETURNING id",
                    String.format("DCT-%06d", serial), warehouse, reason, note, total, user));
                for (Object[] l : lines)
                    TruyVanDB.update(c, "INSERT INTO stock_adjustment_lines(adjustment_id,product_id,bin_id,quantity,loss_value,note) VALUES(?,?,?,?,?,?)", id, l[0], l[1], l[2], l[3], l[4]);
                c.commit();
                return id;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }

    static BigDecimal money(Object raw) {
        try {
            BigDecimal n = new BigDecimal(Objects.toString(raw, "").trim());
            if (n.signum() < 0 || n.scale() > 2 || n.precision() - n.scale() > 16) throw new NumberFormatException();
            return n.setScale(2);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Giá trị tổn thất bắt buộc, không âm, tối đa 2 số lẻ");
        }
    }

    /** Quản lý kho (không phải người lập) duyệt: trừ tồn từng lô/vị trí và ghi thẻ kho ADJUSTMENT. */
    public void approve(long id, long user, Collection<String> roles, Map<String, Object> in) throws SQLException {
        decide(id, user, roles, true, KhoService.text(in.get("note"), "Ý kiến duyệt", 1000, false));
    }

    public void reject(long id, long user, Collection<String> roles, Map<String, Object> in) throws SQLException {
        decide(id, user, roles, false, KhoService.text(in.get("note"), "Lý do từ chối", 1000, true));
    }

    private void decide(long id, long user, Collection<String> roles, boolean approve, String note) throws SQLException {
        if (!KhoService.manager(roles)) throw new SecurityException("Chỉ Quản lý kho được duyệt phiếu điều chỉnh tồn");
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                KhoService.actor(c, user);
                var head = TruyVanDB.one(c, "SELECT * FROM stock_adjustments WHERE id=? FOR UPDATE", id);
                if (head == null) throw new NoSuchElementException("Không tìm thấy phiếu điều chỉnh tồn");
                if (!"PENDING".equals(head.get("status"))) throw new IllegalArgumentException("Phiếu điều chỉnh tồn đã được xử lý");
                if (((Number) head.get("created_by")).longValue() == user) throw new SecurityException("Người lập phiếu không được tự duyệt; cần Quản lý kho khác duyệt");
                long warehouse = ((Number) head.get("warehouse_id")).longValue();
                KhoService.access(c, warehouse, user, roles);
                if (approve) {
                    String reason = "Điều chỉnh tồn " + head.get("code") + " (" + label(String.valueOf(head.get("reason"))) + ")";
                    for (var l : TruyVanDB.rows(c, "SELECT * FROM stock_adjustment_lines WHERE adjustment_id=? ORDER BY id", id))
                        StockLedgerDB.change(c, warehouse, ((Number) l.get("product_id")).longValue(), ((Number) l.get("bin_id")).longValue(),
                            ((BigDecimal) l.get("quantity")).negate(), "ADJUSTMENT", id, TruyVanDB.id(l), user, reason);
                }
                TruyVanDB.update(c, "UPDATE stock_adjustments SET status=?,decided_by=?,decided_at=CURRENT_TIMESTAMP,decision_note=? WHERE id=?",
                    approve ? "APPROVED" : "REJECTED", user, note, id);
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }

    public void cancel(long id, long user, Collection<String> roles, Map<String, Object> in) throws SQLException {
        String note = KhoService.text(in.get("note"), "Lý do huỷ", 1000, true);
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                KhoService.actor(c, user);
                var head = TruyVanDB.one(c, "SELECT * FROM stock_adjustments WHERE id=? FOR UPDATE", id);
                if (head == null) throw new NoSuchElementException("Không tìm thấy phiếu điều chỉnh tồn");
                KhoService.access(c, ((Number) head.get("warehouse_id")).longValue(), user, roles);
                if (!"PENDING".equals(head.get("status"))) throw new IllegalArgumentException("Phiếu điều chỉnh tồn đã được xử lý");
                if (((Number) head.get("created_by")).longValue() != user) throw new SecurityException("Chỉ người lập được huỷ phiếu");
                TruyVanDB.update(c, "UPDATE stock_adjustments SET status='CANCELLED',decided_by=?,decided_at=CURRENT_TIMESTAMP,decision_note=? WHERE id=?", user, note, id);
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }

    static String label(String reason) {
        return switch (reason) {
            case "DAMAGED" -> "hàng hỏng";
            case "LOST" -> "hàng mất";
            case "EXPIRED_DISPOSAL" -> "huỷ hàng hết hạn";
            default -> reason;
        };
    }
}
