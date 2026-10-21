package service;

import db.CongDaiLyDB;
import db.DiemGiaoDB;
import db.DonHangNhapDB;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Cổng đặt hàng dành cho đại lý (S4-10). Đại lý đăng nhập bằng tài khoản vai trò Đại lý (quyền orders.self) đã được
 * liên kết với hồ sơ đại lý. Đại lý chỉ thấy sản phẩm và giá thuộc nhóm khách hàng của mình, xem tổng tiền và công nợ
 * còn lại trước khi gửi; đơn gửi lên luôn ở trạng thái Chờ nhân viên xác nhận.
 *
 * Giá và chiết khấu do máy chủ tính từ bảng giá (đại lý không sửa giá được); giá sàn, cờ duyệt và thông tin nội bộ
 * không bao giờ trả về cho đại lý. Vượt tồn khả dụng hoặc có nợ quá hạn thì không gửi được đơn.
 */
public class CongDaiLyService {

    private static final Set<String> HIDDEN_LINE_FIELDS = Set.of(
        "floorPrice", "floorBasePrice", "listBasePrice", "basePrice", "belowFloor", "priceOverridden", "policyId", "listPrice");

    private final CongDaiLyDB db = new CongDaiLyDB();
    private final DonHangNhapDB orders = new DonHangNhapDB();
    private final TaoDonHangService pricing = new TaoDonHangService();

    Map<String, Object> customer(long userId) throws SQLException {
        var customer = db.customerOf(userId);
        if (customer == null) throw new SecurityException(
            "Tài khoản chưa được liên kết với đại lý. Hãy liên hệ nhân viên kinh doanh phụ trách để được kích hoạt cổng đặt hàng");
        return customer;
    }

    static long id(Map<String, Object> row) {
        return ((Number) row.get("id")).longValue();
    }

    /** Thông tin đại lý, điểm giao, bảng giá và công nợ còn lại. */
    public Map<String, Object> thongTin(long userId) throws SQLException {
        var customer = customer(userId);
        long customerId = id(customer);
        Map<String, Object> out = new LinkedHashMap<>(customer);
        out.remove("salesRepId");
        out.put("deliveryPoints", db.deliveryPoints(customerId));
        Long priceListId = orders.currentPriceListId(customerId);
        out.put("priceList", priceListId == null ? null : db.priceList(priceListId));
        out.put("credit", congNo(orders.creditStatus(customerId), null));
        out.put("canOrder", canOrder(customer, priceListId));
        out.put("cannotOrderReason", cannotOrderReason(customer, priceListId, (Map<?, ?>) out.get("credit")));
        return out;
    }

    static boolean canOrder(Map<String, Object> customer, Long priceListId) {
        return priceListId != null && "ACTIVE".equals(customer.get("status")) && !Boolean.TRUE.equals(customer.get("tradingLocked"))
            && customer.get("salesRepId") != null;
    }

    static String cannotOrderReason(Map<String, Object> customer, Long priceListId, Map<?, ?> credit) {
        if (Boolean.TRUE.equals(customer.get("tradingLocked")))
            return "Đại lý đang bị khoá giao dịch: " + customer.get("lockReason") + ". Hãy liên hệ công ty";
        if (!"ACTIVE".equals(customer.get("status"))) return "Đại lý đã ngừng giao dịch";
        if (customer.get("salesRepId") == null) return "Đại lý chưa có nhân viên phụ trách để xác nhận đơn. Hãy liên hệ công ty";
        if (priceListId == null) return "Chưa có bảng giá còn hiệu lực cho đại lý. Hãy liên hệ nhân viên phụ trách";
        if (credit != null && Boolean.TRUE.equals(credit.get("blocked"))) return String.valueOf(credit.get("blockReason"));
        return "";
    }

    /** Công nợ hiển thị cho đại lý: chỉ số dư của chính mình, không có cờ duyệt nội bộ. */
    static Map<String, Object> congNo(Map<String, Object> credit, BigDecimal orderAmount) {
        Map<String, Object> out = new LinkedHashMap<>();
        BigDecimal limit = (BigDecimal) credit.get("creditLimit");
        BigDecimal outstanding = (BigDecimal) credit.get("outstanding");
        out.put("creditLimit", limit);
        out.put("outstanding", outstanding);
        out.put("available", credit.get("available"));
        if (orderAmount != null) out.put("remainingAfterOrder", limit.subtract(outstanding).subtract(orderAmount));
        out.put("blocked", credit.get("blocked"));
        out.put("blockReason", credit.get("blockReason"));
        return out;
    }

    /** Sản phẩm thuộc bảng giá hiện hành của nhóm khách hàng của đại lý (không gồm SKU chưa có giá). */
    public List<Map<String, Object>> sanPham(long userId, String q) throws SQLException {
        long customerId = id(customer(userId));
        Long priceListId = orders.currentPriceListId(customerId);
        if (priceListId == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (var p : orders.products(q, priceListId)) {
            if (!Boolean.TRUE.equals(p.get("priced"))) continue;
            p.remove("floorPrice");
            p.remove("priced");
            out.add(p);
        }
        return out;
    }

    /** Tính tiền giỏ hàng: giá, chiết khấu, tổng, công nợ còn lại sau đơn và lỗi tồn. Không ghi gì. */
    public Map<String, Object> tinhTien(long userId, Object lines) throws SQLException {
        long customerId = id(customer(userId));
        return anToan(pricing.tinhTienDaiLy(customerId, lines, false));
    }

    /** Bỏ các trường nội bộ khỏi kết quả báo giá trước khi trả cho đại lý. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> anToan(Map<String, Object> priced) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (var l : (List<Map<String, Object>>) priced.get("lines")) {
            Map<String, Object> line = new LinkedHashMap<>(l);
            HIDDEN_LINE_FIELDS.forEach(line::remove);
            lines.add(line);
        }
        out.put("lines", lines);
        out.put("subtotal", priced.get("subtotal"));
        out.put("discount", priced.get("discount"));
        out.put("payable", priced.get("payable"));
        out.put("credit", congNo((Map<String, Object>) priced.get("credit"), (BigDecimal) priced.get("payable")));
        Map<String, Object> stock = new LinkedHashMap<>((Map<String, Object>) priced.get("stock"));
        stock.remove("warehouseId");
        out.put("stock", stock);
        return out;
    }

    /** Gửi đơn: kiểm tra điểm giao, ngày giao, dòng hàng, công nợ quá hạn và tồn; lưu ở Chờ nhân viên xác nhận. */
    public Map<String, Object> guiDon(long userId, Map<String, Object> in) throws SQLException {
        var customer = customer(userId);
        long customerId = id(customer);
        Long priceListId = orders.currentPriceListId(customerId);
        if (!canOrder(customer, priceListId)) throw new IllegalArgumentException(cannotOrderReason(customer, priceListId, null));
        if (in.get("deliveryPointId") == null) throw new IllegalArgumentException("Vui lòng chọn điểm giao hàng");
        long pointId = ChinhSachChietKhauService.positiveId(in.get("deliveryPointId"));
        var point = new DiemGiaoDB().findOfCustomer(pointId, customerId);
        if (point == null) throw new IllegalArgumentException("Điểm giao hàng không thuộc đại lý này");
        String address = point.get("address") + " (Người nhận: " + point.get("receiverName") + ", " + point.get("phone") + ")";
        String d = Objects.toString(in.get("desiredDate"), "").trim();
        if (d.isEmpty()) throw new IllegalArgumentException("Vui lòng chọn ngày giao mong muốn");
        LocalDate date;
        try { date = LocalDate.parse(d); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("Ngày giao mong muốn không hợp lệ"); }
        if (date.isBefore(LocalDate.now())) throw new IllegalArgumentException("Ngày giao mong muốn không được ở quá khứ");
        String note = Objects.toString(in.get("note"), "").trim();
        if (note.length() > 1000) throw new IllegalArgumentException("Ghi chú tối đa 1000 ký tự");
        Map<String, Object> priced = pricing.tinhTienDaiLy(customerId, in.get("lines"), false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) priced.get("lines");
        if (lines.isEmpty()) throw new IllegalArgumentException("Giỏ hàng chưa có sản phẩm");
        Map<?, ?> credit = (Map<?, ?>) priced.get("credit");
        if (Boolean.TRUE.equals(credit.get("blocked"))) throw new IllegalArgumentException(String.valueOf(credit.get("blockReason")));
        Map<?, ?> stock = (Map<?, ?>) priced.get("stock");
        if (!Boolean.TRUE.equals(stock.get("ok"))) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) stock.get("errors");
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        long id = db.createOrder(userId, customerId, ((Number) priced.get("priceListId")).longValue(), pointId, address, date, note,
            (BigDecimal) priced.get("subtotal"), (BigDecimal) priced.get("discount"), (BigDecimal) priced.get("payable"), lines);
        return db.order(customerId, id);
    }

    /** S5-02: POST /portal/orders/{id}/reorder {lineIds?}; trả giỏ mới, không tự gửi đơn. */
    public Map<String,Object> datLai(long userId,long sourceId,Object rawIds)throws SQLException{
        long customerId=id(customer(userId));var source=db.order(customerId,sourceId);
        if(source==null)throw new NoSuchElementException("Không tìm thấy đơn của đại lý");
        if(!Set.of("DELIVERED","CLOSED").contains(source.get("status")))throw new IllegalArgumentException("Chỉ đặt lại đơn đã giao hoặc đã đóng");
        Long priceListId=orders.currentPriceListId(customerId);
        if(priceListId==null)throw new IllegalArgumentException("Đại lý chưa có bảng giá hiện hành");
        Set<Long> selected=null;
        if(rawIds!=null){
            if(!(rawIds instanceof List<?> ids)||ids.isEmpty()||ids.size()>200)throw new IllegalArgumentException("Vui lòng chọn ít nhất một dòng hàng");
            selected=new HashSet<>();for(Object value:ids)selected.add(ChinhSachChietKhauService.positiveId(value));
        }
        @SuppressWarnings("unchecked") var oldLines=(List<Map<String,Object>>)source.get("lines");
        Set<Long> existing=new HashSet<>();for(var l:oldLines)existing.add(((Number)l.get("id")).longValue());
        if(selected!=null&&!existing.containsAll(selected))throw new IllegalArgumentException("Dòng hàng không thuộc đơn đã chọn");
        List<Map<String,Object>> cart=new ArrayList<>(),skipped=new ArrayList<>();
        for(var old:oldLines){
            if(selected!=null&&!selected.contains(((Number)old.get("id")).longValue()))continue;
            long product=((Number)old.get("productId")).longValue();
            var candidates=orders.products(String.valueOf(old.get("sku")),priceListId);
            Map<String,Object> p=candidates.stream().filter(x->((Number)x.get("id")).longValue()==product&&Boolean.TRUE.equals(x.get("priced"))).findFirst().orElse(null);
            var unitRows=new db.CoSoDB().query("SELECT u.id,u.name,u.factor FROM order_lines l JOIN product_units u ON u.id=l.unit_id AND u.active WHERE l.id=?",old.get("id"));
            if(p==null||unitRows.isEmpty()){skipped.add(Map.of("sku",old.get("sku"),"reason","Sản phẩm đã ngừng bán, chưa có giá hiện hành hoặc đơn vị đã ngừng dùng"));continue;}
            var row=new LinkedHashMap<String,Object>();row.put("productId",product);row.put("unitId",unitRows.get(0).get("id"));row.put("quantity",old.get("quantity"));row.put("name",p.get("name"));row.put("sku",p.get("sku"));row.put("units",p.get("units"));cart.add(row);
        }
        if(cart.isEmpty())throw new IllegalArgumentException("Không còn dòng hàng nào có thể đặt lại theo bảng giá hiện hành");
        return Map.of("lines",cart,"skipped",skipped,"sourceCode",source.get("code"),"quote",anToan(pricing.tinhTienDaiLy(customerId,cart,false)));
    }

    public List<Map<String, Object>> donCuaToi(long userId) throws SQLException {
        return db.orders(id(customer(userId)));
    }

    public Map<String, Object> chiTiet(long userId, long orderId) throws SQLException {
        var order = db.order(id(customer(userId)), orderId);
        if (order == null) throw new NoSuchElementException("Không tìm thấy đơn hàng");
        order.put("canCancel", TrangThaiDonHang.PENDING_CONFIRM.equals(order.get("status")));
        return order;
    }

    public Map<String, Object> huy(long userId, long orderId, Object rawReason) throws SQLException {
        String reason = Objects.toString(rawReason, "").trim();
        if (reason.isEmpty()) throw new IllegalArgumentException("Vui lòng nhập lý do huỷ đơn");
        if (reason.length() > 500) throw new IllegalArgumentException("Lý do huỷ tối đa 500 ký tự");
        long customerId = id(customer(userId));
        db.cancel(customerId, orderId, userId, reason);
        return chiTiet(userId, orderId);
    }
}
