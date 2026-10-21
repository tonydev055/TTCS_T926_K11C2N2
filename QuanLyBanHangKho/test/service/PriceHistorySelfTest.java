package service;

import db.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import security.PhanQuyen;
import util.KetNoiDB;

/** S3-02 integration checks; all fixtures are rolled back. */
public class PriceHistorySelfTest {

    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    private static Map<String, Object> priceList(long group, long product, String price) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "Bảng giá kiểm thử S3-02");
        data.put("customer_group_id", group);
        data.put("valid_from", "2099-01-01");
        data.put("valid_to", "2099-12-31");
        data.put("active", false);
        data.put("lines", List.of(Map.of(
            "product_id", product,
            "price", price,
            "floor_price", "80.00"
        )));
        return data;
    }

    public static void main(String[] args) throws Exception {
        try (Connection c = KetNoiDB.getConnection()) {
            long actor = TruyVanDB.id(TruyVanDB.one(c, "SELECT id FROM users ORDER BY id LIMIT 1"));
            DanhMucService.begin(c, actor);
            try {
                String suffix = Long.toString(System.nanoTime());
                long category = TruyVanDB.id(TruyVanDB.one(c,
                    "INSERT INTO categories(code,name) VALUES(?,?) RETURNING id",
                    "S3H-" + suffix, "Nhóm lịch sử giá"));
                long product = TruyVanDB.id(TruyVanDB.one(c,
                    "INSERT INTO products(sku,name,category_id,base_unit,packaging,cost_price) VALUES(?,?,?,?,?,0) RETURNING id",
                    "S3H-" + suffix, "Sản phẩm lịch sử giá", category, "chiếc", ""));
                long group = TruyVanDB.id(TruyVanDB.one(c,
                    "INSERT INTO customer_groups(code,name) VALUES(?,?) RETURNING id",
                    "S3H-" + suffix, "Nhóm khách S3-02"));

                DanhMucService service = new DanhMucService();
                long list = service.save(c, "price_lists", null, priceList(group, product, "100.00"), true);
                var initial = TruyVanDB.one(c,
                    "SELECT old_price,new_price,changed_by_name,effective_at FROM price_history WHERE product_id=? ORDER BY id DESC LIMIT 1",
                    product);
                check(initial.get("old_price") == null, "Initial price has no old value");
                check(new BigDecimal("100.00").compareTo((BigDecimal) initial.get("new_price")) == 0,
                    "Initial price is recorded");
                check(initial.get("changed_by_name") != null, "Editor is recorded");
                check(initial.get("effective_at").toString().equals("2099-01-01"),
                    "Effective date is recorded");

                service.save(c, "price_lists", list, priceList(group, product, "125.00"), true);
                var changed = TruyVanDB.one(c,
                    "SELECT old_price,new_price FROM price_history WHERE product_id=? ORDER BY id DESC LIMIT 1",
                    product);
                check(new BigDecimal("100.00").compareTo((BigDecimal) changed.get("old_price")) == 0,
                    "Old price is recorded");
                check(new BigDecimal("125.00").compareTo((BigDecimal) changed.get("new_price")) == 0,
                    "New price is recorded");

                var response = new LichSuGiaDB().findByProduct(c, product);
                check(Boolean.TRUE.equals(response.get("readOnly")), "History API marks data read-only");
                check(((List<?>) response.get("items")).size() == 2, "History API returns both events");
                check("prices.write".equals(PhanQuyen.permissionFor("/api/price-history", "GET")),
                    "Price history requires price management permission");

                long historyId = TruyVanDB.id(TruyVanDB.one(c,
                    "SELECT id FROM price_history WHERE product_id=? ORDER BY id DESC LIMIT 1", product));
                Savepoint updatePoint = c.setSavepoint();
                boolean updateBlocked = false;
                try {
                    TruyVanDB.update(c, "UPDATE price_history SET new_price=999 WHERE id=?", historyId);
                } catch (SQLException expected) {
                    updateBlocked = true;
                    c.rollback(updatePoint);
                }
                check(updateBlocked, "Database blocks price-history updates");

                Savepoint deletePoint = c.setSavepoint();
                boolean deleteBlocked = false;
                try {
                    TruyVanDB.update(c, "DELETE FROM price_history WHERE id=?", historyId);
                } catch (SQLException expected) {
                    deleteBlocked = true;
                    c.rollback(deletePoint);
                }
                check(deleteBlocked, "Database blocks price-history deletes");
            } finally {
                c.rollback();
            }
        }
        System.out.println("S3-02 price history: " + checks + " checks passed; fixtures rolled back.");
    }
}
