package service;

import db.StockLedgerDB;
import db.TruyVanDB;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import security.PhanQuyen;
import util.KetNoiDB;

/** S6-08: lưu chứng từ giao hàng và ghi nhận hàng hoàn về kho trong một transaction. */
public final class XacNhanGiaoHangService {
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final String SHIPPED = "SHIPPED";

    public Map<String, Object> list(long actor, Collection<String> roles) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            String scope = KhoService.manager(roles) ? ""
                : " AND EXISTS (SELECT 1 FROM user_warehouses uw WHERE uw.user_id=? AND uw.warehouse_id=o.warehouse_id)";
            Object[] orderArgs = KhoService.manager(roles) ? new Object[]{} : new Object[]{actor};
            List<Map<String, Object>> orders = TruyVanDB.rows(c,
                "SELECT o.id,o.code,o.status,o.shipped_at,o.warehouse_id,w.code AS warehouse_code,w.name AS warehouse_name,"
                + "COALESCE(k.code,'') AS customer_code,COALESCE(k.name,cu.full_name,'Khách hàng') AS customer_name,"
                + "o.delivery_address,COUNT(ol.id) AS line_count,"
                + "COALESCE(SUM(COALESCE(ol.delivered_base_quantity,ol.base_quantity,ol.quantity*ol.conversion_factor)),0) AS shipped_quantity "
                + "FROM orders o JOIN warehouses w ON w.id=o.warehouse_id "
                + "LEFT JOIN customers k ON k.id=o.agent_id LEFT JOIN users cu ON cu.id=o.customer_id "
                + "LEFT JOIN order_lines ol ON ol.order_id=o.id "
                + "WHERE o.status='SHIPPED' AND NOT EXISTS (SELECT 1 FROM delivery_confirmations dc WHERE dc.order_id=o.id) "
                + scope + " GROUP BY o.id,w.id,k.id,cu.id ORDER BY o.shipped_at NULLS LAST,o.id DESC LIMIT 200", orderArgs);

            String historyScope = KhoService.manager(roles) ? ""
                : " AND EXISTS (SELECT 1 FROM user_warehouses uw WHERE uw.user_id=? AND uw.warehouse_id=o.warehouse_id)";
            Object[] historyArgs = KhoService.manager(roles) ? new Object[]{} : new Object[]{actor};
            List<Map<String, Object>> history = TruyVanDB.rows(c,
                "SELECT dc.id,dc.order_id,o.code AS order_code,dc.outcome,dc.recipient_name,dc.delivered_at,"
                + "dc.failure_reason,dc.returned_to_warehouse,dc.created_at,w.name AS warehouse_name,"
                + "COALESCE(k.name,cu.full_name,'Khách hàng') AS customer_name,(dc.proof_image_path IS NOT NULL) AS has_proof "
                + "FROM delivery_confirmations dc JOIN orders o ON o.id=dc.order_id "
                + "JOIN warehouses w ON w.id=o.warehouse_id LEFT JOIN customers k ON k.id=o.agent_id "
                + "LEFT JOIN users cu ON cu.id=o.customer_id WHERE TRUE " + historyScope
                + " ORDER BY dc.created_at DESC,dc.id DESC LIMIT 50", historyArgs);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("orders", orders);
            result.put("history", history);
            result.put("canManageAllWarehouses", KhoService.manager(roles));
            return result;
        }
    }

    /**
     * Ghi một kết quả giao cho đơn SHIPPED. Upload ảnh đã được giải mã/chuẩn hóa trước khi lưu.
     * Đơn chỉ được xác nhận một lần; request đồng thời bị tuần tự hóa bằng khóa đơn hàng.
     */
    public Map<String, Object> confirm(long orderId, long actor, Collection<String> roles,
                                       String outcome, String recipientName, String deliveredAtText,
                                       String failureReason, byte[] proofBytes)
        throws SQLException, IOException {
        if (!SUCCESS.equals(outcome) && !FAILED.equals(outcome))
            throw new IllegalArgumentException("Chọn kết quả giao hàng hợp lệ");

        String recipient = KhoService.text(recipientName, "Người nhận", 150, SUCCESS.equals(outcome));
        String reason = KhoService.text(failureReason, "Lý do giao thất bại", 1000, FAILED.equals(outcome));
        LocalDateTime deliveryTime = null;
        if (SUCCESS.equals(outcome)) {
            try {
                deliveryTime = LocalDateTime.parse(Objects.toString(deliveredAtText, ""));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Thời gian giao hàng không hợp lệ");
            }
            if (deliveryTime.isAfter(LocalDateTime.now().plusMinutes(5)))
                throw new IllegalArgumentException("Thời gian giao không được ở tương lai");
            if (proofBytes == null || proofBytes.length == 0)
                throw new IllegalArgumentException("Vui lòng tải ảnh chứng từ giao hàng đã ký");
        }

        Path savedProof = null;
        String proofName = null;

        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                var order = TruyVanDB.one(c,
                    "SELECT id,code,status,warehouse_id FROM orders WHERE id=?", orderId);
                if (order == null) throw new NoSuchElementException("Không tìm thấy đơn hàng");
                if (order.get("warehouse_id") == null)
                    throw new IllegalArgumentException("Đơn hàng chưa được gán kho xuất");
                long warehouse = ((Number) order.get("warehouse_id")).longValue();
                KhoService.access(c, warehouse, actor, roles);
                TruyVanDB.one(c, "SELECT pg_advisory_xact_lock(?)", warehouse);
                order = TruyVanDB.one(c, "SELECT id,code,status,warehouse_id FROM orders WHERE id=? FOR UPDATE", orderId);
                if (order == null || order.get("warehouse_id") == null || ((Number)order.get("warehouse_id")).longValue() != warehouse)
                    throw new IllegalArgumentException("Kho của đơn đã thay đổi, vui lòng tải lại");
                KhoService.actor(c, actor);
                if (!SHIPPED.equals(order.get("status")))
                    throw new IllegalArgumentException("Chỉ xác nhận giao cho đơn ở trạng thái Đã xuất");
                if (TruyVanDB.one(c, "SELECT id FROM delivery_confirmations WHERE order_id=? LIMIT 1", orderId) != null)
                    throw new IllegalArgumentException("Đơn này đã có kết quả giao hàng; không thể xác nhận lần nữa");

                if (SUCCESS.equals(outcome)) {
                    byte[] normalized = HinhAnhService.normalize(proofBytes, false).image();
                    savedProof = saveProof(normalized);
                    proofName = savedProof.getFileName().toString();
                }
                boolean returned = false;
                // S6-07: đơn trên chuyến chưa khởi hành chưa được giao; đơn trên chuyến đang chạy giao thất bại thì
                // hàng vẫn thuộc trách nhiệm chuyến, chỉ nhập lại kho khi kết thúc chuyến.
                var trip = tripOf(c, orderId);
                if (trip != null && "DRAFT".equals(trip.get("status")))
                    throw new IllegalArgumentException("Đơn nằm trong chuyến " + trip.get("code") + " chưa khởi hành; cho chuyến khởi hành trước khi ghi nhận kết quả giao");
                if (FAILED.equals(outcome) && (trip == null || !"IN_TRANSIT".equals(trip.get("status")))) {
                    returned = returnGoods(c, orderId, warehouse, actor, String.valueOf(order.get("code")), reason);
                }

                Timestamp deliveredAt = deliveryTime == null ? null : Timestamp.valueOf(deliveryTime);
                var created = TruyVanDB.one(c,
                    "INSERT INTO delivery_confirmations(order_id,outcome,recipient_name,delivered_at,proof_image_path,failure_reason,returned_to_warehouse,created_by) "
                    + "VALUES(?,?,?,?,?,?,?,?) RETURNING id",
                    orderId, outcome, SUCCESS.equals(outcome) ? recipient : null, deliveredAt,
                    proofName, FAILED.equals(outcome) ? reason : null, returned, actor);
                long confirmationId = TruyVanDB.id(created);

                if (SUCCESS.equals(outcome)) {
                    TrangThaiDonHang.setContext(c, actor, "Giao hàng thành công, xác nhận bởi nhân viên kho");
                    if (TruyVanDB.update(c, "UPDATE orders SET status='DELIVERED' WHERE id=? AND status='SHIPPED'", orderId) != 1)
                        throw new IllegalArgumentException("Trạng thái đơn đã thay đổi, vui lòng tải lại dữ liệu");
                    // Trigger vòng đời tự đặt CURRENT_TIMESTAMP; cập nhật theo thời điểm nhân viên ghi nhận.
                    TruyVanDB.update(c, "UPDATE orders SET delivered_at=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                        deliveredAt, orderId);
                }
                c.commit();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", confirmationId);
                result.put("orderId", orderId);
                result.put("outcome", outcome);
                result.put("returnedToWarehouse", returned);
                result.put("hasProof", proofName != null);
                result.put("message", SUCCESS.equals(outcome) ? "Đã xác nhận giao hàng thành công"
                    : returned ? "Đã ghi nhận giao thất bại và hàng hoàn về kho"
                    : "Đã ghi nhận giao thất bại; hàng vẫn thuộc trách nhiệm chuyến tới khi kết thúc chuyến");
                return result;
            } catch (SQLException e) {
                c.rollback();
                throw TrangThaiDonHang.rethrow(e);
            } catch (RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException | IOException e) {
            if (savedProof != null) Files.deleteIfExists(savedProof);
            throw e;
        }
    }

    /** Trả chứng từ qua endpoint có kiểm tra phân quyền theo kho của đơn. */
    public Path proofPath(long confirmationId, long actor, Collection<String> roles)
        throws SQLException, IOException {
        try (Connection c = KetNoiDB.getConnection()) {
            var row = TruyVanDB.one(c,
                "SELECT dc.proof_image_path,o.warehouse_id FROM delivery_confirmations dc JOIN orders o ON o.id=dc.order_id WHERE dc.id=?",
                confirmationId);
            if (row == null || row.get("proof_image_path") == null)
                throw new NoSuchElementException("Không tìm thấy ảnh chứng từ");
            KhoService.access(c, ((Number) row.get("warehouse_id")).longValue(), actor, roles);
            String stored = String.valueOf(row.get("proof_image_path"));
            // DB chỉ lưu tên UUID, không bao giờ chấp nhận đường dẫn tùy ý.
            if (!stored.matches("[0-9a-fA-F-]{36}\\.png"))
                throw new IOException("Đường dẫn chứng từ không hợp lệ");
            Path file = proofDirectory().resolve(stored).normalize();
            if (!file.getParent().equals(proofDirectory()) || !Files.isRegularFile(file))
                throw new NoSuchElementException("Tệp chứng từ không còn trên máy chủ");
            return file;
        }
    }

    /** Chuyến chứa đơn (bảng của S6-06); null khi đơn giao thẳng không qua chuyến. */
    private static Map<String, Object> tripOf(Connection c, long orderId) throws SQLException {
        return TruyVanDB.one(c, "SELECT t.code,t.status FROM delivery_trip_stops s JOIN delivery_trips t ON t.id=s.trip_id WHERE s.order_id=? FOR SHARE OF t", orderId);
    }

    boolean returnGoods(Connection c, long orderId, long warehouse, long actor,
                                String orderCode, String reason) throws SQLException {
        List<Map<String, Object>> lines = TruyVanDB.rows(c,
            "SELECT ol.id AS line_id,ol.product_id,"
            + "COALESCE(ol.delivered_base_quantity,ol.base_quantity,ol.quantity*ol.conversion_factor) AS return_quantity,"
            + "p.lot_managed FROM order_lines ol JOIN products p ON p.id=ol.product_id WHERE ol.order_id=? ORDER BY ol.id", orderId);
        if (lines.isEmpty()) throw new IllegalArgumentException("Đơn hàng không có dòng hàng để hoàn kho");
        boolean returnedAny = false;

        for (Map<String, Object> line : lines) {
            BigDecimal quantity = (BigDecimal) line.get("return_quantity");
            if (quantity == null || quantity.signum() <= 0) continue;
            long lineId = ((Number) line.get("line_id")).longValue();
            long productId = ((Number) line.get("product_id")).longValue();

            // Hoàn toàn bộ lô đã xuất, không lấy một dòng đại diện.
            var shipments = TruyVanDB.rows(c,
                "SELECT bin_id,delta FROM stock_movements WHERE warehouse_id=? AND product_id=? AND document_id=? AND line_id=? "
                + "AND kind='SHIPMENT_OUT' AND delta<0 ORDER BY bin_id,id FOR UPDATE", warehouse, productId, orderId, lineId);
            BigDecimal dispatched = BigDecimal.ZERO;
            for (var shipment : shipments) dispatched = dispatched.add(((BigDecimal)shipment.get("delta")).abs());
            if (shipments.isEmpty() || dispatched.compareTo(quantity) != 0)
                throw new IllegalArgumentException("Sổ xuất kho không khớp số lượng đã giao; chưa thể hoàn kho an toàn");
            for (var shipment : shipments) {
                if (shipment.get("bin_id") == null) throw new IllegalArgumentException("Bút toán xuất thiếu lô/vị trí");
                StockLedgerDB.change(c, warehouse, productId, ((Number)shipment.get("bin_id")).longValue(),
                    ((BigDecimal)shipment.get("delta")).abs(), "DELIVERY_RETURN", orderId, lineId, actor,
                    "Hàng hoàn về kho từ đơn " + orderCode + ": " + reason);
            }
            returnedAny = true;
        }
        return returnedAny;
    }

    private Path saveProof(byte[] bytes) throws IOException {
        Path dir = proofDirectory();
        Files.createDirectories(dir);
        Path file = dir.resolve(UUID.randomUUID() + ".png").normalize();
        if (!file.getParent().equals(dir)) throw new IOException("Không thể tạo đường dẫn chứng từ");
        Files.write(file, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return file;
    }

    private Path proofDirectory() throws IOException {
        String configured = System.getProperty("khoflow.deliveryProofDir");
        Path base;
        if (configured != null && !configured.isBlank()) base = Paths.get(configured);
        else base = Paths.get(System.getProperty("catalina.base", System.getProperty("java.io.tmpdir")), "data", "delivery-proofs");
        Path dir = base.toAbsolutePath().normalize();
        Files.createDirectories(dir);
        return dir;
    }
}
