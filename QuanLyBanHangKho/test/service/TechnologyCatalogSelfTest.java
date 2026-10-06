package service;

import db.TruyVanDB;
import java.sql.*;
import java.util.*;
import security.PhanQuyen;
import util.KetNoiDB;

public class TechnologyCatalogSelfTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private interface Action { void run() throws Exception; }
    private static void fails(Connection c, Action action, String message) throws Exception {
        var point = c.setSavepoint();
        boolean failed = false;
        try { action.run(); } catch (Exception expected) { failed = true; }
        finally { c.rollback(point); c.releaseSavepoint(point); }
        check(failed, message);
    }
    private static Map<String, Object> sku(String suffix, long model, long category, String color) {
        return new LinkedHashMap<>(Map.of("sku", suffix, "name", "Điện thoại thử " + color,
            "category_id", category, "model_id", model, "base_unit", "Chiếc",
            "active", true, "condition", "NEW", "attributes", Map.of("Màu sắc", color, "Bộ nhớ", "128GB")));
    }
    public static void main(String[] args) throws Exception {
        var service = new DanhMucService();
        String suffix = "TECHTEST" + System.nanoTime();
        try (var c = KetNoiDB.getConnection()) {
            long actor = TruyVanDB.id(TruyVanDB.one(c, "SELECT id FROM users ORDER BY id LIMIT 1"));
            DanhMucService.begin(c, actor);
            try {
                long parent = service.save(c, "categories", null, Map.of("code", suffix, "name", "Công nghệ thử"), false);
                long child = service.save(c, "categories", null, Map.of("code", suffix + "C", "name", "Điện thoại thử", "parent_id", parent), false);
                long brand = service.save(c, "brands", null, Map.of("code", suffix, "name", "Hãng thử"), false);
                var modelData = new LinkedHashMap<String, Object>(Map.of("code", suffix, "name", "Mẫu thử", "category_id", child, "brand_id", brand, "active", true));
                long model = service.save(c, "product_models", null, modelData, false);
                var black = sku(suffix + "BLACK", model, parent, "Đen");
                var blue = sku(suffix + "BLUE", model, child, "Xanh");
                long blackId = service.save(c, "products", null, black, false);
                long blueId = service.save(c, "products", null, blue, false);
                check(blackId != blueId, "Variants have separate SKU records");
                check(((Number) DanhMucService.require(c, "products", blackId).get("category_id")).longValue() == child, "SKU category inherits from model");
                var result = service.page(c, "products", false, false, actor, 1, 20, suffix, "", parent, brand, model);
                check(((Number) result.get("total")).longValue() == 2, "Parent category includes descendants and brand/model filters");
                @SuppressWarnings("unchecked") var items = (List<Map<String, Object>>) result.get("items");
                check(items.stream().allMatch(row -> "Hãng thử".equals(row.get("brand_name")) && "Mẫu thử".equals(row.get("model_name"))), "Read model and brand separately");
                check(items.stream().noneMatch(row -> row.containsKey("cost_price")), "SKU response respects cost permissions");
                check(items.stream().anyMatch(row -> ((Map<?, ?>) row.get("attributes")).get("Màu sắc").equals("Đen")), "Attributes returned as an object");
                black.put("attributes", Map.of("Màu sắc", "Đen", "Bộ nhớ", "256GB"));
                service.save(c, "products", blackId, black, false);
                check(TruyVanDB.rows(c, "SELECT * FROM product_attributes WHERE product_id=?", blackId).size() == 2, "Attribute edits replace rows without duplicating keys");
                black.remove("attributes");
                service.save(c, "products", blackId, black, false);
                check(TruyVanDB.rows(c, "SELECT * FROM product_attributes WHERE product_id=?", blackId).size() == 2, "Legacy updates preserve existing attributes");
                fails(c, () -> service.save(c, "products", null, blue, false), "Duplicate SKU rejected");
                fails(c, () -> service.delete(c, "brands", brand), "Referenced brand cannot be deleted");
                fails(c, () -> service.delete(c, "product_models", model), "Model with variants cannot be deleted");
                fails(c, () -> DanhMucService.validateAttributes(Map.of("RAM", "8GB", "ram", "16GB")), "Case-insensitive duplicate attribute rejected");
                fails(c, () -> DanhMucService.validateAttributes(Map.of("RAM", List.of("8GB"))), "Nested attribute rejected");
                var invalid = new LinkedHashMap<>(black);
                invalid.put("condition", "BROKEN");
                fails(c, () -> service.save(c, "products", blackId, invalid, false), "Invalid condition rejected");
                invalid.put("condition", "USED"); invalid.put("model_id", null);
                fails(c, () -> service.save(c, "products", blackId, invalid, false), "Existing SKU cannot be unlinked from its model");
                modelData.put("category_id", parent);
                service.save(c, "product_models", model, modelData, false);
                check(((Number) DanhMucService.require(c, "products", blackId).get("category_id")).longValue() == parent, "Model category edits keep SKU category consistent");
                modelData.put("active", false);
                service.save(c, "product_models", model, modelData, false);
                check(((Number) service.page(c, "products", false, true, actor, 1, 20, suffix, "").get("total")).longValue() == 0, "Customer cannot see inactive models' variants");
                fails(c, () -> service.save(c, "products", null, sku(suffix + "NEW", model, child, "Trắng"), false), "Cannot add variants to an inactive model");
                check("products.write".equals(PhanQuyen.permissionFor("/api/product-models/1", "PUT")), "Model writes use existing product permission");
                check("products.read".equals(PhanQuyen.permissionFor("/api/brands/", "GET")), "Brand reads use existing product permission");
            } finally { c.rollback(); }
        }
        System.out.println("Technology catalog: " + checks + " checks passed; fixtures rolled back.");
    }
}
