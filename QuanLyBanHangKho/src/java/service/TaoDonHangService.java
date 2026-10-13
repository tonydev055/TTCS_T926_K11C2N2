package service;

import db.ChinhSachChietKhauDB;
import db.DiemGiaoDB;
import db.DonHangNhapDB;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Tạo đơn hàng cho đại lý (S3-09, S4-01). Mọi số tiền do máy chủ tính: giá lấy từ bảng giá hiện hành của đại lý,
 * đại lý lấy từ hồ sơ đại lý (customers): chỉ đại lý mình phụ trách, đang giao dịch và không bị khoá mới tạo được đơn.
 * Chiết khấu dùng lại logic "có lợi nhất" của S3-01; bậc xét theo tổng số lượng SKU hoặc nhóm hàng trong cả đơn và
 * được tính lại mỗi lần số lượng thay đổi.
 *
 * S4-01: nhân viên được sửa đơn giá thủ công (unitPrice, theo đơn vị tính đã chọn). Giá sửa thấp hơn giá sàn của
 * bảng giá thì đơn bị đánh dấu cần duyệt (BELOW_FLOOR). Tiền hàng của dòng sửa giá = đơn giá nhập × số lượng;
 * giá cơ bản lưu 6 số lẻ, chiết khấu S3-01 tính trên giá cơ bản làm tròn 2 số lẻ.
 */
public class TaoDonHangService {

    private static final int MAX_LINES = 200;
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000000000");
    private final DonHangNhapDB dao = new DonHangNhapDB();

    private static BigDecimal clean(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    /** Định dạng tiền cho thông báo: 34.000 đ, 1.250,5 đ. */
    public static String tien(BigDecimal v) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", sym).format(v) + " đ";
    }

    static Map<String, Object> flag(String code, String message) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("code", code);
        f.put("message", message);
        return f;
    }

    /** Đơn giá thủ công (theo đơn vị đã chọn); null khi không sửa giá. */
    static BigDecimal manualPrice(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        BigDecimal v = ChinhSachChietKhauService.decimal(raw);
        if (v.signum() < 0) throw new IllegalArgumentException("đơn giá không được âm");
        if (v.compareTo(MAX_PRICE) > 0) throw new IllegalArgumentException("đơn giá quá lớn");
        if (clean(v).scale() > 2) throw new IllegalArgumentException("đơn giá tối đa 2 chữ số thập phân");
        return v;
    }

    /** S4-04: lịch sử mua 3 tháng của đại lý mình phụ trách (ngoài phạm vi thì như không tồn tại). */
    public Map<String, Object> lichSuMua(long userId, long customerId) throws SQLException {
        if (!dao.isAgent(customerId, userId)) throw new NoSuchElementException("Không tìm thấy đại lý");
        return dao.purchaseHistory(customerId, dao.currentPriceListId(customerId));
    }

    /** Công nợ hiện tại, hạn mức, phần còn lại và tình trạng quá hạn của đại lý mình phụ trách (S4-02). */
    public Map<String, Object> congNo(long userId, long customerId) throws SQLException {
        if (!dao.isAgent(customerId, userId)) throw new NoSuchElementException("Không tìm thấy đại lý");
        return dao.creditStatus(customerId);
    }

    /**
     * S4-02: so tổng đơn cộng công nợ hiện tại với hạn mức. Vượt hạn mức thì đơn cần duyệt (OVER_CREDIT).
     * Trả về thông tin công nợ kèm số còn lại sau đơn này.
     */
    Map<String, Object> kiemTraHanMuc(long customerId, BigDecimal payable, List<Map<String, Object>> flags) throws SQLException {
        Map<String, Object> credit = dao.creditStatus(customerId);
        BigDecimal limit = (BigDecimal) credit.get("creditLimit");
        BigDecimal outstanding = (BigDecimal) credit.get("outstanding");
        BigDecimal after = outstanding.add(payable);
        credit.put("orderAmount", payable);
        credit.put("afterOrder", after);
        credit.put("remainingAfterOrder", limit.subtract(after));
        boolean over = payable.signum() > 0 && after.compareTo(limit) > 0;
        credit.put("overLimit", over);
        if (over) flags.add(flag("OVER_CREDIT", limit.signum() == 0
            ? "Đại lý không được nợ (hạn mức 0 đ): đơn " + tien(payable) + " cần duyệt"
            : "Vượt hạn mức công nợ " + tien(after.subtract(limit)) + ": công nợ hiện tại " + tien(outstanding) +
              " + đơn này " + tien(payable) + " = " + tien(after) + ", hạn mức " + tien(limit)));
        return credit;
    }

    /**
     * S4-03: tồn khả dụng tại kho phục vụ của đại lý cho từng dòng. Mỗi dòng có available (theo đơn vị đã chọn),
     * maxQuantity (tối đa còn đặt được sau khi trừ các dòng khác cùng SKU) và stockShort. Thiếu tồn chặn gửi đơn.
     */
    Map<String, Object> kiemTraTon(long customerId, List<Map<String, Object>> lines) throws SQLException {
        Map<String, Object> stock = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        var warehouse = db.GiuChoTonDB.customerWarehouse(customerId);
        stock.put("warehouseId", warehouse == null ? null : warehouse.get("id"));
        stock.put("warehouseName", warehouse == null ? "" : warehouse.get("name"));
        stock.put("errors", errors);
        if (warehouse == null) {
            errors.add("Đại lý chưa được gán kho phục vụ nên chưa xác định được tồn khả dụng. " +
                "Hãy gán kho phục vụ trong hồ sơ đại lý");
            for (var line : lines) line.put("stockShort", false);
            stock.put("ok", false);
            return stock;
        }
        Map<Long, BigDecimal> totalBase = new HashMap<>();
        for (var line : lines) totalBase.merge(((Number) line.get("productId")).longValue(),
            ((BigDecimal) line.get("quantity")).multiply((BigDecimal) line.get("conversionFactor")), BigDecimal::add);
        Map<Long, BigDecimal> available = db.GiuChoTonDB.available(((Number) warehouse.get("id")).longValue(), totalBase.keySet());
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            long product = ((Number) line.get("productId")).longValue();
            BigDecimal factor = (BigDecimal) line.get("conversionFactor");
            BigDecimal own = ((BigDecimal) line.get("quantity")).multiply(factor);
            BigDecimal avail = available.get(product);
            BigDecimal forThisLine = avail.subtract(totalBase.get(product).subtract(own));
            BigDecimal max = db.GiuChoTonDB.inUnit(forThisLine, factor);
            boolean shortage = totalBase.get(product).compareTo(avail) > 0;
            line.put("available", db.GiuChoTonDB.inUnit(avail, factor));
            line.put("maxQuantity", max);
            line.put("stockShort", shortage);
            if (shortage) errors.add("Dòng " + (i + 1) + " · " + line.get("sku") + ": vượt tồn khả dụng, " +
                (max.signum() > 0 ? "chỉ đặt được tối đa " + clean(max).toPlainString() + " " + line.get("unitName")
                    : "đã hết hàng") + " tại " + warehouse.get("name"));
        }
        stock.put("ok", errors.isEmpty());
        return stock;
    }

    /**
     * Tính giá, chiết khấu, phải thu cho các dòng hàng; không ghi gì vào database.
     * Kết quả có "flags": các lý do khiến đơn cần duyệt nếu gửi ngay.
     */
    public Map<String, Object> tinhTien(long userId, long customerId, Object rawLines) throws SQLException {
        if (!dao.isAgent(customerId, userId)) throw new NoSuchElementException("Không tìm thấy đại lý");
        return tinhTienDaiLy(customerId, rawLines, true);
    }

    /**
     * Tính tiền cho một đại lý đã được xác minh quyền truy cập.
     * allowManualPrice=false bỏ qua mọi đơn giá gửi lên (dùng cho kênh đại lý tự đặt).
     */
    public Map<String, Object> tinhTienDaiLy(long customerId, Object rawLines, boolean allowManualPrice) throws SQLException {
        Long priceListId = dao.currentPriceListId(customerId);
        if (priceListId == null) throw new IllegalArgumentException(
            "Đại lý chưa có bảng giá còn hiệu lực. Hãy kiểm tra nhóm khách hàng và bảng giá của đại lý.");
        List<?> raw;
        if (rawLines == null) raw = List.of();
        else if (rawLines instanceof List<?> l) raw = l;
        else throw new IllegalArgumentException("Danh sách dòng hàng không hợp lệ");
        if (raw.size() > MAX_LINES) throw new IllegalArgumentException("Một đơn tối đa " + MAX_LINES + " dòng hàng");

        // Bước 1: kiểm tra từng dòng, quy đổi số lượng ra đơn vị cơ sở, xác định đơn giá áp dụng.
        List<Map<String, Object>> lines = new ArrayList<>();
        List<Map<String, Object>> quoteInput = new ArrayList<>();
        List<Map<String, Object>> flags = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            try {
                if (!(raw.get(i) instanceof Map<?, ?> m)) throw new IllegalArgumentException("Dòng hàng không hợp lệ");
                long productId = ChinhSachChietKhauService.positiveId(m.get("productId"));
                long unitId = ChinhSachChietKhauService.positiveId(m.get("unitId"));
                BigDecimal qty = ChinhSachChietKhauService.decimal(m.get("quantity"));
                if (qty.signum() <= 0 || qty.scale() > 6) throw new IllegalArgumentException("Số lượng phải lớn hơn 0");
                var info = dao.lineInfo(priceListId, productId, unitId);
                if (info == null) {
                    var product = dao.productName(productId);
                    throw new IllegalArgumentException((product == null ? "sản phẩm" : product) +
                        " chưa có giá trong bảng giá đang hiệu lực của đại lý hoặc đơn vị tính không hợp lệ. " +
                        "Hãy bổ sung giá vào bảng giá trước khi bán mặt hàng này");
                }
                BigDecimal factor = (BigDecimal) info.get("factor");
                BigDecimal listBase = (BigDecimal) info.get("price");
                BigDecimal floorBase = (BigDecimal) info.get("floor_price");
                BigDecimal baseQty = qty.multiply(factor);
                if (baseQty.stripTrailingZeros().scale() > 0) throw new IllegalArgumentException(
                    "số lượng quy đổi ra đơn vị cơ bản phải là số nguyên");
                if (baseQty.compareTo(BigDecimal.valueOf(1_000_000_000L)) > 0) throw new IllegalArgumentException("Số lượng quá lớn");
                int bq = baseQty.setScale(0, RoundingMode.UNNECESSARY).intValueExact();

                BigDecimal manual = allowManualPrice ? manualPrice(m.get("unitPrice")) : null;
                BigDecimal basePrice = listBase;
                boolean overridden = false;
                if (manual != null) {
                    BigDecimal manualBase = manual.divide(factor, 6, RoundingMode.HALF_UP);
                    if (manualBase.compareTo(listBase) != 0) {
                        basePrice = manualBase;
                        overridden = true;
                    }
                }
                boolean belowFloor = overridden && basePrice.compareTo(floorBase) < 0;

                Map<String, Object> line = new LinkedHashMap<>();
                line.put("productId", productId);
                line.put("sku", info.get("sku"));
                line.put("name", info.get("name"));
                line.put("unitId", unitId);
                line.put("unitName", info.get("unit_name"));
                line.put("quantity", clean(qty));
                line.put("conversionFactor", factor);
                line.put("basePrice", basePrice);
                line.put("listBasePrice", listBase);
                line.put("floorBasePrice", floorBase);
                line.put("unitPrice", overridden ? manual.setScale(2, RoundingMode.HALF_UP)
                    : basePrice.multiply(factor).setScale(2, RoundingMode.HALF_UP));
                line.put("listPrice", listBase.multiply(factor).setScale(2, RoundingMode.HALF_UP));
                line.put("floorPrice", floorBase.multiply(factor).setScale(2, RoundingMode.HALF_UP));
                line.put("priceOverridden", overridden);
                line.put("belowFloor", belowFloor);
                if (belowFloor) flags.add(flag("BELOW_FLOOR", "Dòng " + (i + 1) + " · " + info.get("sku") + ": giá " +
                    tien((BigDecimal) line.get("unitPrice")) + "/" + info.get("unit_name") + " thấp hơn giá sàn " +
                    tien((BigDecimal) line.get("floorPrice")) + "/" + info.get("unit_name")));
                lines.add(line);
                quoteInput.add(Map.of("productId", productId, "quantity", bq,
                    "unitPrice", basePrice.setScale(2, RoundingMode.HALF_UP)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Dòng " + (i + 1) + ": " + e.getMessage());
            }
        }

        // Bước 2: chiết khấu "có lợi nhất" (S3-01), bậc xét theo tổng số lượng SKU / nhóm hàng trong cả đơn.
        var quotes = new ChinhSachChietKhauDB().quoteOrder(quoteInput);
        BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO, payable = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            var quote = quotes.get(i);
            var line = lines.get(i);
            BigDecimal lineSubtotal = (BigDecimal) quote.get("subtotal");
            BigDecimal lineDiscount = (BigDecimal) quote.get("discount");
            BigDecimal linePayable = (BigDecimal) quote.get("payment");
            if (Boolean.TRUE.equals(line.get("priceOverridden"))) {
                // Giá sửa tay: tiền hàng theo đúng đơn giá nhập, không lệch do quy đổi ra đơn vị cơ bản.
                lineSubtotal = ((BigDecimal) line.get("unitPrice")).multiply((BigDecimal) line.get("quantity"))
                    .setScale(2, RoundingMode.HALF_UP);
                linePayable = lineSubtotal.subtract(lineDiscount);
            }
            line.put("subtotal", lineSubtotal);
            line.put("discount", lineDiscount);
            line.put("payable", linePayable);
            line.put("policyId", quote.get("policyId"));
            subtotal = subtotal.add(lineSubtotal);
            discount = discount.add(lineDiscount);
            payable = payable.add(linePayable);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("priceListId", priceListId);
        out.put("lines", lines);
        out.put("subtotal", subtotal);
        out.put("discount", discount);
        out.put("payable", payable);
        out.put("credit", kiemTraHanMuc(customerId, payable, flags));
        out.put("stock", kiemTraTon(customerId, lines));
        out.put("flags", flags);
        out.put("needsApproval", !flags.isEmpty());
        return out;
    }

    /**
     * S4-09: sao chép một đơn cũ (mọi trạng thái, kể cả đã huỷ) của đại lý mình phụ trách thành đơn Nháp mới.
     * Giữ đại lý, dòng hàng (sản phẩm, đơn vị, số lượng), điểm giao nếu còn dùng và ghi chú; giá và chiết khấu
     * được áp lại theo bảng giá hiện hành (không giữ giá cũ hay giá sửa tay). Dòng không còn bán được thì bỏ qua và
     * báo lại. Đại lý bị khoá hoặc có nợ quá hạn thì không sao chép được (như tạo đơn mới).
     */
    public Map<String, Object> saoChep(long sourceId, long userId) throws SQLException {
        var source = dao.copySource(sourceId, userId);
        if (source == null) throw new NoSuchElementException("Không tìm thấy đơn hàng để sao chép");
        long customerId = ((Number) source.get("customerId")).longValue();
        Long priceListId = dao.currentPriceListId(customerId);
        if (priceListId == null) throw new IllegalArgumentException(
            "Đại lý chưa có bảng giá còn hiệu lực. Hãy kiểm tra nhóm khách hàng và bảng giá của đại lý.");
        List<Map<String, Object>> lines = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sourceLines = (List<Map<String, Object>>) source.get("lines");
        for (var l : sourceLines) {
            long productId = ((Number) l.get("productId")).longValue(), unitId = ((Number) l.get("unitId")).longValue();
            if (dao.lineInfo(priceListId, productId, unitId) == null) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("sku", l.get("sku"));
                s.put("name", l.get("name"));
                s.put("reason", Boolean.TRUE.equals(l.get("productActive"))
                    ? "chưa có giá trong bảng giá hiện hành hoặc đơn vị tính đã ngừng dùng" : "sản phẩm đã ngừng kinh doanh");
                skipped.add(s);
            } else lines.add(Map.of("productId", productId, "unitId", unitId, "quantity", l.get("quantity")));
        }
        if (lines.isEmpty()) throw new IllegalArgumentException(
            "Không còn dòng hàng nào của đơn " + source.get("code") + " bán được theo bảng giá hiện hành");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        body.put("deliveryPointId", source.get("deliveryPointId"));
        body.put("note", source.get("note"));
        body.put("lines", lines);
        long id = luuNhap(null, userId, body);
        Map<String, Object> out = new LinkedHashMap<>(dao.load(id, userId));
        out.put("copiedFrom", source.get("code"));
        out.put("skipped", skipped);
        return out;
    }

    /** Lưu nháp: tạo mới (orderId == null) hoặc cập nhật. Nháp được phép chưa đủ địa chỉ, ngày giao hoặc dòng hàng. */
    public long luuNhap(Long orderId, long userId, Map<String, Object> in) throws SQLException {
        return luu(orderId, userId, in, false);
    }

    /**
     * Gửi đơn: tính lại giá và lưu toàn bộ nội dung trong một giao dịch. Không vi phạm gì thì hệ thống tự duyệt,
     * ngược lại đơn chuyển sang Chờ duyệt kèm lý do.
     */
    public long hoanTat(long orderId, long userId, Map<String, Object> in) throws SQLException {
        return luu(orderId, userId, in, true);
    }

    private long luu(Long orderId, long userId, Map<String, Object> in, boolean complete) throws SQLException {
        if (in.get("customerId") == null) throw new IllegalArgumentException("Vui lòng chọn đại lý");
        long customerId = ChinhSachChietKhauService.positiveId(in.get("customerId"));
        if (orderId != null) {
            var old = dao.load(orderId, userId);
            if (old == null || !"DRAFT".equals(old.get("status")))
                throw new NoSuchElementException("Không tìm thấy đơn nháp để cập nhật");
        }
        if (complete && in.get("deliveryPointId") == null)
            throw new IllegalArgumentException("Vui lòng chọn điểm giao hàng trước khi gửi đơn");
        Map<String, Object> priced = tinhTien(userId, customerId, in.get("lines"));
        // S4-02: có nợ quá hạn thì chặn hoàn toàn việc tạo đơn mới và gửi đơn (database cũng chặn lần cuối).
        Map<?, ?> credit = (Map<?, ?>) priced.get("credit");
        if ((orderId == null || complete) && Boolean.TRUE.equals(credit.get("blocked")))
            throw new IllegalArgumentException(String.valueOf(credit.get("blockReason")));
        // S4-03: vượt tồn khả dụng thì chặn gửi đơn, kèm số lượng tối đa còn đặt được.
        Map<?, ?> stock = (Map<?, ?>) priced.get("stock");
        if (complete && !Boolean.TRUE.equals(stock.get("ok"))) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) stock.get("errors");
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        // Điểm giao phải nằm trong danh sách của ĐÚNG đại lý này (kiểm tra ở máy chủ, không chỉ ẩn trên giao diện).
        Long pointId = null;
        String address = "";
        if (in.get("deliveryPointId") != null) {
            pointId = ChinhSachChietKhauService.positiveId(in.get("deliveryPointId"));
            var point = new DiemGiaoDB().findOfCustomer(pointId, customerId);
            if (point == null) throw new IllegalArgumentException("Điểm giao hàng không thuộc đại lý này");
            // Chép địa chỉ vào đơn để đơn cũ không đổi khi điểm giao bị sửa về sau.
            address = point.get("address") + " (Người nhận: " + point.get("receiverName") + ", " + point.get("phone") + ")";
        }
        String note = Objects.toString(in.get("note"), "").trim();
        if (note.length() > 1000) throw new IllegalArgumentException("Ghi chú tối đa 1000 ký tự");
        LocalDate date = null;
        String d = Objects.toString(in.get("desiredDate"), "").trim();
        if (!d.isEmpty()) {
            try { date = LocalDate.parse(d); }
            catch (DateTimeParseException e) { throw new IllegalArgumentException("Ngày giao mong muốn không hợp lệ"); }
        }
        if (date != null && date.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Ngày giao mong muốn không được ở quá khứ");
        if (complete && date == null)
            throw new IllegalArgumentException("Vui lòng chọn ngày giao mong muốn trước khi gửi đơn");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) priced.get("lines");
        if (complete && lines.isEmpty()) throw new IllegalArgumentException("Đơn hàng phải có ít nhất một sản phẩm");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> flags = complete ? (List<Map<String, Object>>) priced.get("flags") : null;
        return dao.saveDraft(orderId, userId, customerId, ((Number) priced.get("priceListId")).longValue(),
            pointId, address, date, note, (BigDecimal) priced.get("subtotal"), (BigDecimal) priced.get("discount"),
            (BigDecimal) priced.get("payable"), lines, flags);
    }
}
