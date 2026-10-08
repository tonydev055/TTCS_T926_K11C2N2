package service;

import db.ChinhSachChietKhauDB;
import db.DiemGiaoDB;
import db.DonHangNhapDB;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Tạo đơn hàng cho đại lý (S3-09). Mọi số tiền do máy chủ tính: giá lấy từ bảng giá hiện hành của đại lý,

 * Đại lý lấy từ hồ sơ đại lý (customers): chỉ đại lý mình phụ trách, đang giao dịch và không bị khoá mới tạo được đơn.
 * Chiết khấu dùng lại logic "có lợi nhất" của S3-01 (ChinhSachChietKhauService.quote).
 */
public class TaoDonHangService {

    private static final int MAX_LINES = 200;
    private final DonHangNhapDB dao = new DonHangNhapDB();

    private static BigDecimal clean(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    /** Tính giá, chiết khấu, phải thu cho các dòng hàng; không ghi gì vào database. */
    public Map<String, Object> tinhTien(long userId, long customerId, Object rawLines) throws SQLException {
        if (!dao.isAgent(customerId, userId)) throw new NoSuchElementException("Không tìm thấy đại lý");
        Long priceListId = dao.currentPriceListId(customerId);
        if (priceListId == null) throw new IllegalArgumentException(
            "Đại lý chưa có bảng giá còn hiệu lực. Hãy kiểm tra nhóm khách hàng và bảng giá của đại lý.");
        List<?> raw;
        if (rawLines == null) raw = List.of();
        else if (rawLines instanceof List<?> l) raw = l;
        else throw new IllegalArgumentException("Danh sách dòng hàng không hợp lệ");
        if (raw.size() > MAX_LINES) throw new IllegalArgumentException("Một đơn tối đa " + MAX_LINES + " dòng hàng");

        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO, payable = BigDecimal.ZERO;

        for (int i = 0; i < raw.size(); i++) {
            try {
                if (!(raw.get(i) instanceof Map<?, ?> m)) throw new IllegalArgumentException("Dòng hàng không hợp lệ");
                long productId = ChinhSachChietKhauService.positiveId(m.get("productId"));
                long unitId = ChinhSachChietKhauService.positiveId(m.get("unitId"));
                BigDecimal qty = ChinhSachChietKhauService.decimal(m.get("quantity"));
                if (qty.signum() <= 0 || qty.scale() > 6) throw new IllegalArgumentException("Số lượng phải lớn hơn 0");
                var info = dao.lineInfo(priceListId, productId, unitId);
                if (info == null) throw new IllegalArgumentException(
                    "sản phẩm chưa có giá trong bảng giá hiện hành hoặc đơn vị tính không hợp lệ");
                BigDecimal factor = (BigDecimal) info.get("factor");
                BigDecimal basePrice = (BigDecimal) info.get("price");
                BigDecimal baseQty = qty.multiply(factor);
                if (baseQty.stripTrailingZeros().scale() > 0) throw new IllegalArgumentException(
                    "số lượng quy đổi ra đơn vị cơ bản phải là số nguyên");
                if (baseQty.compareTo(BigDecimal.valueOf(1_000_000_000L)) > 0) throw new IllegalArgumentException("Số lượng quá lớn");
                int bq = baseQty.setScale(0, RoundingMode.UNNECESSARY).intValueExact();

                var quote = new ChinhSachChietKhauDB().quote(Map.of("productId", productId, "quantity", bq, "unitPrice", basePrice));
                BigDecimal lineSubtotal = (BigDecimal) quote.get("subtotal");
                BigDecimal lineDiscount = (BigDecimal) quote.get("discount");
                BigDecimal linePayable = (BigDecimal) quote.get("payment");

                Map<String, Object> line = new LinkedHashMap<>();
                line.put("productId", productId);
                line.put("sku", info.get("sku"));
                line.put("name", info.get("name"));
                line.put("unitId", unitId);
                line.put("unitName", info.get("unit_name"));
                line.put("quantity", clean(qty));
                line.put("conversionFactor", factor);
                line.put("basePrice", basePrice);
                line.put("unitPrice", basePrice.multiply(factor).setScale(2, RoundingMode.HALF_UP));
                line.put("subtotal", lineSubtotal);
                line.put("discount", lineDiscount);
                line.put("payable", linePayable);
                line.put("policyId", quote.get("policyId"));
                lines.add(line);
                subtotal = subtotal.add(lineSubtotal);
                discount = discount.add(lineDiscount);
                payable = payable.add(linePayable);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Dòng " + (i + 1) + ": " + e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("priceListId", priceListId);
        out.put("lines", lines);
        out.put("subtotal", subtotal);
        out.put("discount", discount);
        out.put("payable", payable);
        return out;
    }

    /** Lưu nháp: tạo mới (orderId == null) hoặc cập nhật. Nháp được phép chưa đủ địa chỉ, ngày giao hoặc dòng hàng. */
    public long luuNhap(Long orderId, long userId, Map<String, Object> in) throws SQLException {
        if (in.get("customerId") == null) throw new IllegalArgumentException("Vui lòng chọn đại lý");
        long customerId = ChinhSachChietKhauService.positiveId(in.get("customerId"));
        Map<String, Object> priced = tinhTien(userId, customerId, in.get("lines"));

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
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) priced.get("lines");
        return dao.saveDraft(orderId, userId, customerId, ((Number) priced.get("priceListId")).longValue(),
            pointId, address, date, note, (BigDecimal) priced.get("subtotal"), (BigDecimal) priced.get("discount"),
            (BigDecimal) priced.get("payable"), lines);
    }
}
