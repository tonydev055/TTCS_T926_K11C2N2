package service;

import db.TruyVanDB;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import security.PhanQuyen;
import util.*;

/** Integration tests run in a transaction that is always rolled back. */
public class Sprint2SelfTest {

    private static int checks;
    private static Connection c;

    private interface Task {
        void run() throws Exception;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    private static void fails(Task action, String message) throws Exception {
        Savepoint point = c.setSavepoint();
        boolean failed = false;
        try {
            action.run();
        } catch (Exception expected) {
            failed = true;
        } finally {
            c.rollback(point);
            c.releaseSavepoint(point);
        }
        check(failed, message);
    }

    private static Map<String, Object> product(String sku, long category) {
        return new LinkedHashMap<>(
            Map.of(
                "sku",
                sku,
                "name",
                "Sản phẩm thử nghiệm",
                "category_id",
                category,
                "base_unit",
                "lon",
                "packaging",
                "24 lon / thùng",
                "cost_price",
                "12000.50",
                "active",
                true
            )
        );
    }

    public static void main(String[] args) throws Exception {
        check(
            ThuDienTuService.passwordResetUrl("http://localhost:8080/QuanLyBanHangKho/", "test").equals(
                "http://localhost:8080/QuanLyBanHangKho/index.html?resetToken=test"
            ),
            "Reset link preserves deployment context"
        );
        check(
            ThuDienTuService.passwordResetUrl("https://example.test/app", "a+b").equals(
                "https://example.test/app/index.html?resetToken=a%2Bb"
            ),
            "Reset link handles missing slash and encodes token"
        );
        check(
            ThuDienTuService.passwordResetUrl("http://localhost:8080/", "test").equals(
                "http://localhost:8080/index.html?resetToken=test"
            ),
            "Reset link supports root deployment"
        );
        DanhMucService service = new DanhMucService();
        String suffix = Long.toString(System.nanoTime());
        try (Connection connection = KetNoiDB.getConnection()) {
            c = connection;
            long actor = TruyVanDB.id(TruyVanDB.one(c, "SELECT id FROM users ORDER BY id LIMIT 1"));
            DanhMucService.begin(c, actor);
            try {
                long a = service.save(
                    c,
                    "categories",
                    null,
                    Map.of("code", "TEST-A" + suffix, "name", "Cấp 1"),
                    false
                );
                long b = service.save(
                    c,
                    "categories",
                    null,
                    Map.of("code", "TEST-B" + suffix, "name", "Cấp 2", "parent_id", a),
                    false
                );
                long d = service.save(
                    c,
                    "categories",
                    null,
                    Map.of("code", "TEST-C" + suffix, "name", "Cấp 3", "parent_id", b),
                    false
                );
                check(
                    (
                        (Number) DanhMucService.require(c, "categories", d).get("parent_id")
                    ).longValue() == b,
                    "Three-level category tree"
                );
                fails(
                    () ->
                        service.save(
                            c,
                            "categories",
                            a,
                            Map.of("code", "TEST-A" + suffix, "name", "Cycle", "parent_id", d),
                            false
                        ),
                    "Reject category cycle"
                );
                long p = service.save(c, "products", null, product("TEST-P" + suffix, d), true);
                fails(
                    () -> service.save(c, "products", null, product("TEST-P" + suffix, d), true),
                    "Unique SKU"
                );
                fails(
                    () ->
                        service.save(c, "products", null, product("TEST-OTHER" + suffix, d), false),
                    "Deny cost writes for admin/non-manager"
                );
                check(
                    !service
                        .list(c, "products", false, false, actor)
                        .stream()
                        .filter(row -> TruyVanDB.id(row) == p)
                        .findFirst()
                        .orElseThrow()
                        .containsKey("cost_price"),
                    "Non-manager responses contain no cost field"
                );
                check(
                    service
                        .list(c, "products", true, false, actor)
                        .stream()
                        .filter(row -> TruyVanDB.id(row) == p)
                        .findFirst()
                        .orElseThrow()
                        .containsKey("cost_price"),
                    "Manager receives cost"
                );
                fails(
                    () -> service.delete(c, "categories", d),
                    "Cannot delete category with products"
                );
                fails(
                    () -> service.delete(c, "categories", a),
                    "Cannot delete category with children"
                );
                Map<String, Object> changed = product("TEST-P" + suffix, b);
                service.save(c, "products", p, changed, true);
                check(
                    (
                        (Number) DanhMucService.require(c, "products", p).get("category_id")
                    ).longValue() == b,
                    "Move product to another category"
                );
                changed.put("base_unit", "kg");
                fails(() -> service.save(c, "products", p, changed, true), "Base unit immutable");
                long unit = service.save(
                    c,
                    "product_units",
                    null,
                    Map.of("product_id", p, "name", "thùng", "factor", "24"),
                    false
                );
                fails(
                    () ->
                        service.save(
                            c,
                            "product_units",
                            null,
                            Map.of("product_id", p, "name", "lốc", "factor", 0),
                            false
                        ),
                    "Reject zero conversion factor"
                );
                fails(
                    () ->
                        service.save(
                            c,
                            "product_units",
                            null,
                            Map.of("product_id", p, "name", "lon", "factor", 2),
                            false
                        ),
                    "Base factor is always one"
                );
                long group = service.save(
                    c,
                    "customer_groups",
                    null,
                    Map.of("code", "TEST-G" + suffix, "name", "Nhóm thử"),
                    false
                );
                Map<String, Object> prices = new LinkedHashMap<>(
                    Map.of(
                        "name",
                        "Bảng giá thử",
                        "customer_group_id",
                        group,
                        "valid_from",
                        "2030-01-01",
                        "valid_to",
                        "2030-01-31",
                        "active",
                        true,
                        "lines",
                        List.of(Map.of("product_id", p, "price", 20000, "floor_price", 15000))
                    )
                );
                long price = service.save(c, "price_lists", null, prices, false);
                fails(
                    () -> service.save(c, "price_lists", null, prices, false),
                    "Reject overlapping effective price lists"
                );
                Map<String, Object> badPrices = new LinkedHashMap<>(prices);
                badPrices.put(
                    "lines",
                    List.of(Map.of("product_id", p, "price", 10, "floor_price", 20))
                );
                fails(
                    () -> service.save(c, "price_lists", price, badPrices, false),
                    "Floor cannot exceed sale price"
                );
                long order = TruyVanDB.id(
                    TruyVanDB.one(
                        c,
                        "INSERT INTO orders(code,price_list_id) VALUES(?,?) RETURNING id",
                        "TEST-O" + suffix,
                        price
                    )
                );
                long line = TruyVanDB.id(
                    TruyVanDB.one(
                        c,
                        "INSERT INTO order_lines(order_id,product_id,unit_id,quantity,conversion_factor) VALUES(?,?,?,2,999) RETURNING id",
                        order,
                        p,
                        unit
                    )
                );
                var snapshot = TruyVanDB.one(
                    c,
                    "SELECT conversion_factor,base_quantity FROM order_lines WHERE id=?",
                    line
                );
                check(
                    new BigDecimal("24").compareTo(
                        (BigDecimal) snapshot.get("conversion_factor")
                    ) == 0,
                    "Snapshot uses server factor, not client input"
                );
                check(
                    new BigDecimal("48").compareTo((BigDecimal) snapshot.get("base_quantity")) == 0,
                    "Order quantity converted to base"
                );
                long nextUnit = service.save(
                    c,
                    "product_units",
                    unit,
                    Map.of("product_id", p, "name", "thùng", "factor", 30),
                    false
                );
                check(nextUnit != unit, "Conversion uses new immutable version");
                check(
                    new BigDecimal("48").compareTo(
                        (BigDecimal) TruyVanDB.one(
                            c,
                            "SELECT base_quantity FROM order_lines WHERE id=?",
                            line
                        ).get("base_quantity")
                    ) == 0,
                    "Old line quantity remains unchanged"
                );
                fails(
                    () ->
                        TruyVanDB.update(
                            c,
                            "UPDATE order_lines SET conversion_factor=100 WHERE id=?",
                            line
                        ),
                    "Cannot overwrite historical factor"
                );
                fails(
                    () -> service.save(c, "price_lists", price, prices, false),
                    "Used price list cannot be modified"
                );
                fails(
                    () -> service.delete(c, "price_lists", price),
                    "Used price list cannot be deleted"
                );
                fails(() -> service.delete(c, "products", p), "Used product cannot be deleted");
                Map<String, Object> version = new LinkedHashMap<>(prices);
                version.put("valid_from", "2030-02-01");
                version.put("valid_to", "2030-02-28");
                version.put("previous_id", price);
                long newPrice = service.save(c, "price_lists", null, version, false);
                check(
                    (
                        (Number) DanhMucService.require(c, "price_lists", newPrice).get("version")
                    ).intValue() == 2,
                    "New price version keeps prior data"
                );
                long supplier = service.save(
                    c,
                    "suppliers",
                    null,
                    Map.of(
                        "code",
                        "TEST-S" + suffix,
                        "name",
                        "NCC thử",
                        "tax_code",
                        "1234567890",
                        "contact_name",
                        "Người thử",
                        "payment_terms",
                        "30 ngày",
                        "phone",
                        "0901234567"
                    ),
                    false
                );
                long warehouse = TruyVanDB.id(TruyVanDB.one(c, "SELECT id FROM warehouses LIMIT 1"));
                long receipt = TruyVanDB.id(
                    TruyVanDB.one(
                        c,
                        "INSERT INTO stock_receipts(code,supplier_id,warehouse_id) VALUES(?,?,?) RETURNING id",
                        "TEST-R" + suffix,
                        supplier,
                        warehouse
                    )
                );
                TruyVanDB.update(
                    c,
                    "INSERT INTO stock_receipt_lines(receipt_id,product_id,unit_id,quantity,conversion_factor) VALUES(?,?,?,3,999)",
                    receipt,
                    p,
                    nextUnit
                );
                check(
                    new BigDecimal("90").compareTo(
                        (BigDecimal) TruyVanDB.one(
                            c,
                            "SELECT base_quantity FROM stock_receipt_lines WHERE receipt_id=?",
                            receipt
                        ).get("base_quantity")
                    ) == 0,
                    "Receipt uses current conversion snapshot"
                );
                fails(
                    () -> service.delete(c, "suppliers", supplier),
                    "Supplier with receipt cannot be deleted"
                );
                check(
                    TruyVanDB.one(
                        c,
                        "SELECT id FROM audit_logs WHERE object_type='products' AND object_id=? AND actor_user_id=? AND new_value LIKE '%cost_price%'",
                        String.valueOf(p),
                        actor
                    ) != null,
                    "Transactional audit has actor and changed values"
                );
                check(
                    DanhMucService.phone("+84901234567", true).equals("+84901234567"),
                    "Vietnam phone accepted"
                );
                fails(() -> DanhMucService.phone("12345", true), "Invalid phone rejected");
                check(
                    PhanQuyen.allows(
                        List.of("SALES_MANAGER"),
                        PhanQuyen.permissionFor("/api/products/", "POST")
                    ),
                    "Sales manager may create products"
                );
                check(
                    !PhanQuyen.allows(
                        List.of("SALES_REP"),
                        PhanQuyen.permissionFor("/api/products/", "POST")
                    ),
                    "Sales rep cannot create products"
                );
                check(
                    PhanQuyen.allows(
                        List.of("WAREHOUSE"),
                        PhanQuyen.permissionFor("/api/product-units/", "PUT")
                    ),
                    "Warehouse may manage unit conversions"
                );
                check(
                    !PhanQuyen.allows(List.of("ADMIN"), "products.cost"),
                    "Admin does not receive cost exception"
                );
                List<TepExcelService.Row> importRows = List.of(
                    new TepExcelService.Row(
                        2,
                        Map.of(
                            "sku",
                            "TEST-P" + suffix,
                            "name",
                            "Cập nhật",
                            "category_code",
                            "TEST-B" + suffix,
                            "base_unit",
                            "lon",
                            "packaging",
                            "",
                            "active",
                            "true",
                            "cost_price",
                            "13000"
                        ),
                        ""
                    ),
                    new TepExcelService.Row(
                        3,
                        Map.of(
                            "sku",
                            "TEST-I" + suffix,
                            "name",
                            "Hàng mới",
                            "category_code",
                            "TEST-B" + suffix,
                            "base_unit",
                            "lon",
                            "packaging",
                            "",
                            "active",
                            "true",
                            "cost_price",
                            "1000"
                        ),
                        ""
                    ),
                    new TepExcelService.Row(
                        4,
                        Map.of(
                            "sku",
                            "INVALID" + suffix,
                            "name",
                            "Sai",
                            "category_code",
                            "NO-SUCH-CATEGORY",
                            "base_unit",
                            "lon",
                            "active",
                            "true",
                            "cost_price",
                            "1"
                        ),
                        ""
                    )
                );
                NhapDuLieuService importer = new NhapDuLieuService();
                var preview = importer.process(c, "products", importRows, true, false);
                check(
                    preview
                        .stream()
                        .filter(row -> Boolean.TRUE.equals(row.get("valid")))
                        .count() == 2,
                    "Preview separates invalid rows"
                );
                check(
                    TruyVanDB.one(c, "SELECT id FROM products WHERE sku=?", "TEST-I" + suffix) == null,
                    "Preview does not persist rows"
                );
                check(
                    DanhMucService.require(c, "products", p)
                        .get("name")
                        .equals("Sản phẩm thử nghiệm"),
                    "Preview does not update existing SKU"
                );
                var imported = importer.process(c, "products", importRows, true, true);
                check(
                    imported.get(0).get("action").equals("Cập nhật"),
                    "Existing SKU marked update"
                );
                check(
                    TruyVanDB.one(c, "SELECT id FROM products WHERE sku=?", "TEST-I" + suffix) != null,
                    "Valid row imported even when another row fails"
                );
                check(
                    DanhMucService.require(c, "products", p).get("name").equals("Cập nhật"),
                    "Existing SKU updated on confirm"
                );
                var userRows = List.of(
                    new TepExcelService.Row(
                        2,
                        Map.of(
                            "username",
                            "test" + suffix,
                            "email",
                            "test" + suffix + "@example.test",
                            "full_name",
                            "Người kiểm thử",
                            "phone",
                            "0901234567",
                            "role",
                            "WAREHOUSE",
                            "warehouse_code",
                            "",
                            "territory",
                            ""
                        ),
                        ""
                    ),
                    new TepExcelService.Row(
                        3,
                        Map.of(
                            "username",
                            "testok" + suffix,
                            "email",
                            "testok" + suffix + "@example.test",
                            "full_name",
                            "Người kiểm thử",
                            "phone",
                            "0901234567",
                            "role",
                            "CUSTOMER",
                            "warehouse_code",
                            "",
                            "territory",
                            ""
                        ),
                        ""
                    )
                );
                var users = importer.process(c, "users", userRows, false, true);
                check(
                    !Boolean.TRUE.equals(users.get(0).get("valid")) &&
                        Boolean.TRUE.equals(users.get(1).get("valid")),
                    "User import validates role/warehouse and skips invalid row"
                );
                check(
                    TruyVanDB.one(
                        c,
                        "SELECT id FROM dev_mailbox_messages WHERE recipient=?",
                        "testok" + suffix + "@example.test"
                    ) != null,
                    "Imported user gets activation mailbox message"
                );
                var importedUser = TruyVanDB.one(
                    c,
                    "SELECT password_hash,requires_password_change FROM users WHERE email=?",
                    "testok" + suffix + "@example.test"
                );
                check(
                    MatKhauUtil.verify(
                        "Demo@123",
                        String.valueOf(importedUser.get("password_hash"))
                    ),
                    "Imported user receives default demo password"
                );
                check(
                    Boolean.TRUE.equals(importedUser.get("requires_password_change")),
                    "Imported user must change temporary password"
                );
                check(
                    String.valueOf(
                        TruyVanDB.one(
                            c,
                            "SELECT body FROM dev_mailbox_messages WHERE recipient=?",
                            "testok" + suffix + "@example.test"
                        ).get("body")
                    ).contains("Demo@123"),
                    "Mailbox contains default temporary password"
                );
                var userDao = new db.NguoiDungDB();
                long deleteId = TruyVanDB.id(
                    TruyVanDB.one(
                        c,
                        "SELECT id FROM users WHERE email=?",
                        "testok" + suffix + "@example.test"
                    )
                );
                fails(() -> userDao.deleteUser(c, actor, actor), "Self deletion rejected");
                long auditId = TruyVanDB.id(
                    TruyVanDB.one(
                        c,
                        "INSERT INTO audit_logs(actor_user_id,action,object_type) VALUES(?,'TEST','users') RETURNING id",
                        deleteId
                    )
                );
                fails(
                    () -> userDao.deleteUser(c, deleteId, actor),
                    "User with audit history cannot be deleted"
                );
                check(
                    TruyVanDB.one(c, "SELECT user_id FROM user_roles WHERE user_id=?", deleteId) != null,
                    "Failed delete preserves roles"
                );
                TruyVanDB.update(c, "DELETE FROM audit_logs WHERE id=?", auditId);
                check(
                    userDao.deleteUser(c, deleteId, actor),
                    "Unused imported user can be deleted"
                );
                check(
                    TruyVanDB.one(c, "SELECT id FROM users WHERE id=?", deleteId) == null &&
                        TruyVanDB.one(c, "SELECT user_id FROM user_roles WHERE user_id=?", deleteId) ==
                            null,
                    "Delete removes user and role links"
                );
                check(
                    TruyVanDB.one(
                        c,
                        "SELECT id FROM audit_logs WHERE actor_user_id=? AND object_type='users' AND object_id=? AND action='DELETE'",
                        actor,
                        String.valueOf(deleteId)
                    ) != null,
                    "Deletion records actor and target in audit"
                );
                check(
                    !userDao.deleteUser(c, deleteId, actor),
                    "Repeated deletion reports missing user"
                );
                var customerPrices = service.list(c, "price_lists", false, true, actor);
                check(
                    customerPrices.isEmpty(),
                    "Customer without assigned group sees no other group prices"
                );
                BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(image, "png", bytes);
                var images = HinhAnhService.normalize(bytes.toByteArray(), true);
                var avatar = ImageIO.read(new ByteArrayInputStream(images.image()));
                check(avatar.getWidth() == 256 && avatar.getHeight() == 256, "Avatar square crop");
                check(
                    ImageIO.read(new ByteArrayInputStream(images.thumbnail())).getWidth() == 64,
                    "Avatar thumbnail"
                );
                fails(
                    () -> HinhAnhService.normalize(new byte[2097153], true),
                    "Oversized avatar rejected"
                );
                fails(
                    () -> HinhAnhService.normalize("not an image".getBytes(), true),
                    "Fake image rejected"
                );
                List<String> headers = NhapDuLieuService.headers("products", true);
                byte[] template = TepExcelService.template(headers);
                byte[] workbook = withRows(
                    template,
                    "<row r=\"2\"><c r=\"A2\" t=\"inlineStr\"><is><t>SKU-X</t></is></c><c r=\"B2\" t=\"inlineStr\"><is><t>Sữa</t></is></c></row><row r=\"3\"><c r=\"A3\"><f>1+1</f><v>2</v></c></row>"
                );
                var xlsxRows = TepExcelService.read(new ByteArrayInputStream(workbook), headers);
                check(
                    xlsxRows.size() == 2 && xlsxRows.get(0).values().get("name").equals("Sữa"),
                    "Read actual XLSX rows and Unicode"
                );
                check(!xlsxRows.get(1).error().isEmpty(), "Excel formulas rejected per row");
                Object json = XuLyJson.parse(
                    "{\"items\":[{\"name\":\"a\\t\\r\\n\\\"b\",\"price\":0.25}],\"ok\":true}"
                );
                check(XuLyJson.parse(XuLyJson.stringify(json)).equals(json), "Nested JSON round trip");
                fails(
                    () -> XuLyJson.parse("{\"id\":1,\"id\":2}"),
                    "Ambiguous duplicate JSON fields rejected"
                );
            } finally {
                c.rollback();
            }
        }
        System.out.println(
            "Sprint 2: " + checks + " checks passed; all database fixtures rolled back."
        );
    }

    private static byte[] withRows(byte[] source, String rows) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (
            ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(source));
            ZipOutputStream out = new ZipOutputStream(bytes)
        ) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                byte[] data = in.readAllBytes();
                if (e.getName().equals("xl/worksheets/sheet1.xml")) data = new String(
                    data,
                    StandardCharsets.UTF_8
                )
                    .replace("</sheetData>", rows + "</sheetData>")
                    .getBytes(StandardCharsets.UTF_8);
                out.putNextEntry(new ZipEntry(e.getName()));
                out.write(data);
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
