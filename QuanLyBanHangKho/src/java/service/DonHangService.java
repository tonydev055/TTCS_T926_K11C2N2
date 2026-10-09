package service;

/** Business service for Order. Add transaction rules here instead of placing them in JSP. */
public class DonHangService {
    /** Quote a line in base SKU units. Caller supplies the authoritative price-list price. */
    public java.util.Map<String,Object> tinhGiaDong(long productId, int quantity,
                                                   java.math.BigDecimal price) throws java.sql.SQLException {
        return new db.ChinhSachChietKhauDB().quote(java.util.Map.of(
            "productId",productId,"quantity",quantity,"unitPrice",price));
    }
}
