import db.TruyVanDB;
import service.*;
import util.KetNoiDB;
import util.MatKhauUtil;
import java.sql.*;
import java.nio.file.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.List;

/** Bộ dữ liệu bàn giao tự nguyện, chỉ chạy trên database demo mới, không thuộc WAR. */
public final class SetupLecturerDemo {
    static final List<String> WH = List.of("WH_MANAGER"), STAFF = List.of("WAREHOUSE"),
        SALES = List.of("SALES_REP"), MANAGER = List.of("SALES_MANAGER"), ACC = List.of("ACCOUNTANT");
    static long rep, manager, warehouse, staff, accountant, supplier, group, defect;
    static final List<Long> products = new ArrayList<>(), units = new ArrayList<>(), customers = new ArrayList<>();
    static final List<Long> warehouses = new ArrayList<>(), locations = new ArrayList<>();

    static long number(Object value) { return ((Number)value).longValue(); }
    static long id(Connection c, String sql, Object... p) throws SQLException {
        return TruyVanDB.id(TruyVanDB.one(c, sql, p));
    }
    static long lookup(String sql, Object... p) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) { return id(c, sql, p); }
    }
    static List<Map<String,Object>> rows(String sql, Object... p) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) { return TruyVanDB.rows(c, sql, p); }
    }

    public static void main(String[] args) throws Exception {
        String database = System.getenv("LECTURER_DATABASE");
        String base = System.getenv("LECTURER_JDBC_BASE");
        if (database == null || !database.matches("khoflow_demo(?:_[a-z0-9_]+)?") || base == null
            || !Objects.equals(System.getenv("DB_URL"), base + "/" + database))
            throw new IllegalArgumentException("Chỉ tạo database riêng có tên khoflow_demo hoặc khoflow_demo_...");
        Class.forName("org.postgresql.Driver");
        if (args.length == 1 && args[0].equals("--check-ready")) {
            try (Connection c = KetNoiDB.getConnection()) {
                if (TruyVanDB.one(c,"SELECT 1 FROM lecturer_demo_installation WHERE id=1 AND version=1 AND completed") == null)
                    throw new IllegalStateException("Database demo chưa khởi tạo hoàn tất");
            }
            System.out.println("DEMO_READY: " + database); return;
        }
        try (Connection c = DriverManager.getConnection(base + "/postgres", System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))) {
            if (TruyVanDB.one(c, "SELECT 1 FROM pg_database WHERE datname=?", database) != null)
                throw new IllegalStateException("Database đã tồn tại: " + database + ". Không ghi đè; chọn tên demo mới.");
            try (Statement s = c.createStatement()) { s.execute("CREATE DATABASE \"" + database + "\" ENCODING 'UTF8' TEMPLATE template0"); }
        }
        // Tên database được kiểm tra trước CREATE; lỗi giữ nguyên DB để chẩn đoán, không tự DROP.
        try (Connection c = KetNoiDB.getConnection(); Statement s = c.createStatement()) {
            for (String line : Files.readAllLines(Path.of("scripts/lecturer-migrations.txt"))) {
                if (line.isBlank() || line.startsWith("#")) continue;
                s.execute(Files.readString(Path.of("database", line.trim())));
                System.out.println("Migration: " + line);
            }
            s.execute("CREATE TABLE lecturer_demo_installation (id integer PRIMARY KEY CHECK(id=1), version integer NOT NULL, installed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, completed boolean NOT NULL DEFAULT false)");
            s.execute("INSERT INTO lecturer_demo_installation(id,version) VALUES(1,1)");
        }
        masters();
        receiveStock();
        List<Long> invoices = new ArrayList<>();
        List<Long> delivered = new ArrayList<>();
        // Mỗi đại lý có ba đơn giao thật; hoá đơn cũ có ngày phát hành quá khứ để xem tuổi nợ.
        for (int dealer = 0; dealer < customers.size(); dealer++) {
            for (int n = 0; n < 3; n++) {
                long order = order(dealer, dealer + n, 20 + n * 5, "DELIVERED");
                delivered.add(order);
            }
        }
        // Tạo đơn đang xử lý trước khi xuất hiện công nợ quá hạn trong bộ demo.
        for (int dealer = 0; dealer < customers.size(); dealer++) order(dealer, 0, 5, "DRAFT");
        for (String state : List.of("APPROVED", "PICKING", "SHIPPED")) order(0, 1, 5, state);
        for (int dealer = 0; dealer < customers.size(); dealer++)
            for (int n = 0; n < 3; n++) invoices.add(invoice(delivered.get(dealer * 3 + n), dealer, n == 0 ? 0 : n == 1 ? 45 : 100));
        for (int dealer = 0; dealer < customers.size(); dealer++) {
            new ThanhToanService().create(accountant, Map.of("customerId", customers.get(dealer),
                "invoiceId", invoices.get(dealer * 3), "amount", "50000", "paymentMethod", "BANK_TRANSFER",
                "receivedDate", LocalDate.now().toString(), "requestKey", UUID.randomUUID().toString(),
                "note", "Khoản thu minh hoạ bàn giao giảng viên"));
        }
        returns(invoices.get(0), true, false);
        returns(invoices.get(3), true, true);
        returns(invoices.get(6), false, false);
        long bin = lookup("SELECT id FROM stock_bins WHERE warehouse_id=? AND on_hand>10 ORDER BY id LIMIT 1", warehouses.get(0));
        long adjustment = new DieuChinhTonService().create(staff, STAFF, Map.of("warehouseId", warehouses.get(0),
            "reason", "DAMAGED", "note", "Bao bì hỏng trong bộ demo", "lines", List.of(Map.of("binId", bin, "quantity", "2", "lossValue", "10000"))));
        new DieuChinhTonService().approve(adjustment, warehouse, WH, Map.of("note", "Đã kiểm tra hàng demo"));
        YearMonth month = YearMonth.now();
        for (int back = 0; back < 2; back++) {
                new ChiTieuDoanhSoService().save(null, manager, MANAGER, Map.of("month", month.plusMonths(back).toString(),
                "staffId", rep, "region", "Miền Nam", "targetAmount", "5000000", "note", "Chỉ tiêu demo giảng viên"));
        }
        // Đồng bộ cảnh báo sau khi đã tạo đủ đơn cho các màn hình thao tác.
        new CanhBaoCongNoService().daily();
        verify();
        try (Connection c = KetNoiDB.getConnection()) {
            TruyVanDB.update(c, "UPDATE lecturer_demo_installation SET completed=true WHERE id=1");
        }
        System.out.println("DEMO_READY: " + database + "; 7 tài khoản, 6 SKU, 3 đại lý, 9 hoá đơn và các chứng từ S3–S8.");
    }

    static void masters() throws Exception {
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Mật khẩu chỉ cho tài khoản trong DB demo; dùng encoder thật của ứng dụng.
                String encoded = MatKhauUtil.encode("Demo@123");
                TruyVanDB.update(c, "UPDATE users SET password_hash=?,requires_password_change=false", encoded);
                rep = id(c, "SELECT id FROM users WHERE username='sales.rep'");
                manager = id(c, "SELECT id FROM users WHERE username='sales.manager'");
                warehouse = id(c, "SELECT id FROM users WHERE username='warehouse.manager'");
                staff = id(c, "SELECT id FROM users WHERE username='warehouse.staff'");
                accountant = id(c, "SELECT id FROM users WHERE username='accountant.demo'");
                group = id(c, "INSERT INTO customer_groups(code,name) VALUES('DEMO-DL','Đại lý demo giảng viên') RETURNING id");
                TruyVanDB.update(c, "UPDATE users SET customer_group_id=? WHERE username='customer.demo'", group);
                supplier = id(c, "INSERT INTO suppliers(code,name,tax_code,contact_name,phone) VALUES('DEMO-NCC','Nhà cung cấp demo','','Liên hệ demo','0900000000') RETURNING id");
                long parent = id(c, "INSERT INTO categories(code,name) VALUES('DEMO-HANG','Hàng tiêu dùng demo') RETURNING id");
                long drinks = id(c, "INSERT INTO categories(code,name,parent_id) VALUES('DEMO-UONG','Đồ uống',?) RETURNING id", parent);
                long foods = id(c, "INSERT INTO categories(code,name,parent_id) VALUES('DEMO-THUC','Thực phẩm',?) RETURNING id", parent);
                String[] names = {"Nước khoáng 500ml", "Trà đào 450ml", "Sữa hộp 180ml", "Bánh quy 200g", "Đường 1kg", "Nước mắm 500ml"};
                for (int i = 0; i < names.length; i++) {
                    long product = id(c, "INSERT INTO products(sku,name,category_id,base_unit,cost_price,lot_managed) VALUES(?,?,?,?,?,true) RETURNING id",
                        "DEMO-SP-0" + (i + 1), names[i], i < 3 ? drinks : foods, "Cái", new BigDecimal(5000 + i * 2000));
                    products.add(product);
                    units.add(id(c, "INSERT INTO product_units(product_id,name,factor) VALUES(?,'Cái',1) RETURNING id", product));
                    TruyVanDB.update(c, "INSERT INTO product_units(product_id,name,factor) VALUES(?,'Thùng',24)", product);
                }
                long priceList = id(c, "INSERT INTO price_lists(name,customer_group_id,valid_from,valid_to,version) VALUES('Bảng giá demo hiện hành',?,CURRENT_DATE-180,CURRENT_DATE+365,1) RETURNING id", group);
                for (int i = 0; i < products.size(); i++) TruyVanDB.update(c,
                    "INSERT INTO price_list_lines(price_list_id,product_id,price,floor_price) VALUES(?,?,?,?)",
                    priceList, products.get(i), new BigDecimal(10000 + i * 3000), new BigDecimal(7000 + i * 2000));
                long policy = id(c, "INSERT INTO discount_policies(name,scope,category_id,dtype) VALUES('Chiết khấu demo theo sản lượng','GROUP',?,'PERCENT') RETURNING id", drinks);
                TruyVanDB.update(c, "INSERT INTO discount_tiers(policy_id,qty_from,value) VALUES(?,10,2),(?,50,5)", policy, policy);
                for (String code : List.of("HCM", "HN", "DN")) {
                    long wh = id(c, "SELECT id FROM warehouses WHERE code=?", code); warehouses.add(wh);
                    locations.add(id(c, "INSERT INTO warehouse_locations(warehouse_id,code,name) VALUES(?,'DEMO-A','Kệ A – demo') RETURNING id", wh));
                    TruyVanDB.update(c, "INSERT INTO user_warehouses(user_id,warehouse_id) VALUES(?,?) ON CONFLICT DO NOTHING", staff, wh);
                    TruyVanDB.update(c, "INSERT INTO user_warehouses(user_id,warehouse_id) VALUES(?,?) ON CONFLICT DO NOTHING", warehouse, wh);
                }
                defect = id(c, "INSERT INTO warehouses(code,name,is_defect) VALUES('DEMO-LOI','Kho hàng lỗi demo',true) RETURNING id");
                for (int i = 0; i < 3; i++) {
                    long customer = id(c, "INSERT INTO customers(code,name,customer_group_id,region,sales_rep_id,warehouse_id,credit_limit,credit_days,phone) VALUES(?,?,?,?,?,?,100000000,30,'0900000000') RETURNING id",
                        "DEMO-DL-0" + (i+1), new String[]{"Đại lý An Bình demo", "Đại lý Bình Minh demo", "Đại lý Thành Công demo"}[i], group,
                        i == 0 ? "Miền Nam" : i == 1 ? "Miền Bắc" : "Miền Trung", rep, warehouses.get(i));
                    customers.add(customer);
                    TruyVanDB.update(c, "INSERT INTO customer_addresses(customer_id,address,recipient_name,phone,is_default) VALUES(?,?,?,'0900000000',true)", customer,
                        "Địa chỉ bàn giao demo " + (i+1), "Người nhận demo " + (i+1));
                }
                TruyVanDB.update(c, "UPDATE customers SET portal_user_id=(SELECT id FROM users WHERE username='customer.demo') WHERE id=?", customers.get(0));
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    static void receiveStock() throws Exception {
        for (int w = 0; w < warehouses.size(); w++) {
            List<Map<String,Object>> lines = new ArrayList<>();
            for (int p = 0; p < products.size(); p++) lines.add(Map.of("productId", products.get(p), "unitId", units.get(p),
                "locationId", locations.get(w), "quantity", p == 5 ? "60" : "500", "lotNumber", "DEMO-LO-" + (p+1),
                "expiryDate", LocalDate.now().plusDays(p == 2 ? 20 : 365).toString(), "manufacturedDate", LocalDate.now().minusDays(60).toString()));
            long receipt = new PhieuNhapKhoService().save(null, null, staff, STAFF, Map.of("warehouseId", warehouses.get(w), "supplierId", supplier,
                "documentNumber", "DEMO-NHAP-" + (w+1), "date", LocalDate.now().toString(), "note", "Tồn đầu bộ demo giảng viên", "lines", lines));
            new PhieuNhapKhoService().confirm(receipt, warehouse, WH);
        }
    }

    static long order(int dealer, int productIndex, int qty, String state) throws Exception {
        int p = productIndex % products.size();
        long customer = customers.get(dealer), wh = warehouses.get(dealer);
        long address = lookup("SELECT id FROM customer_addresses WHERE customer_id=? AND is_default", customer);
        Map<String,Object> input = Map.of("customerId", customer, "deliveryPointId", address, "desiredDate", LocalDate.now().toString(), "note", "Đơn demo giảng viên – " + state,
            "lines", List.of(Map.of("productId", products.get(p), "unitId", units.get(p), "quantity", Integer.toString(qty))));
        TaoDonHangService service = new TaoDonHangService();
        long order = service.luuNhap(null, rep, input);
        if (state.equals("DRAFT")) return order;
        service.hoanTat(order, rep, input);
        String status = String.valueOf(rows("SELECT status FROM orders WHERE id=?", order).get(0).get("status"));
        if (status.equals("PENDING_APPROVAL")) new DuyetDonService().quyetDinh(order, "APPROVE", "Duyệt đơn demo", manager);
        if (state.equals("APPROVED")) return order;
        long pick = new PhieuSoanService().create(staff, STAFF, Map.of("warehouseId", wh, "orderIds", List.of(order)));
        if (state.equals("PICKING")) return order;
        List<Map<String,Object>> picks = new ArrayList<>();
        for (var line : rows("SELECT id,requested_quantity FROM pick_list_lines WHERE pick_list_id=?", pick))
            picks.add(Map.of("lineId", line.get("id"), "actualQuantity", line.get("requested_quantity")));
        new PhieuSoanService().saveLines(pick, staff, STAFF, Map.of("lines", picks));
        List<Map<String,Object>> shipments = new ArrayList<>();
        for (var line : rows("SELECT id,quantity FROM order_lines WHERE order_id=?", order))
            shipments.add(Map.of("lineId", line.get("id"), "actualQuantity", line.get("quantity")));
        new XuatKhoService().confirm(order, staff, STAFF, Map.of("lines", shipments));
        if (state.equals("SHIPPED")) return order;
        BufferedImage image = new BufferedImage(640, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0,0,640,200); g.setColor(Color.BLACK); g.setFont(new Font("SansSerif",Font.BOLD,24));
        g.drawString("DEMO - PHIEU GIAO HANG",30,70); g.drawString("Minh hoa, khong phai chung tu that",30,120); g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream(); ImageIO.write(image, "png", png);
        new XacNhanGiaoHangService().confirm(order, staff, STAFF, "SUCCESS", "Người nhận demo", LocalDateTime.now().minusMinutes(1).toString(), "", png.toByteArray());
        return order;
    }

    static long invoice(long order, int dealer, int age) throws Exception {
        // Snapshot hoá đơn lịch sử trong DB demo: giữ trigger/FK, lấy toàn bộ tiền từ đơn đã giao.
        // Không sửa ngày trên chứng từ bất biến hoặc thay đổi mã nghiệp vụ của service vận hành.
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                long serial = number(TruyVanDB.one(c,"UPDATE invoice_number_counters SET last_number=last_number+1 WHERE document_type='INVOICE' RETURNING last_number").get("last_number"));
                long invoice = id(c, "INSERT INTO invoices(invoice_number,customer_id,period_from,period_to,subtotal,discount_total,total_amount,issued_by,issued_at,note) SELECT ?,?,CURRENT_DATE-?,CURRENT_DATE-?,subtotal,discount_total,payable,?,CURRENT_TIMESTAMP-(? * INTERVAL '1 day'),'Hoá đơn lịch sử demo giảng viên' FROM orders WHERE id=? RETURNING id",
                    String.format("HD-%06d", serial), customers.get(dealer), age, age, accountant, age, order);
                TruyVanDB.update(c, "INSERT INTO invoice_deliveries(invoice_id,order_id,delivery_confirmation_id) SELECT ?,?,id FROM delivery_confirmations WHERE order_id=?", invoice, order, order);
                TruyVanDB.update(c, "INSERT INTO invoice_lines(invoice_id,order_id,order_line_id,product_id,unit_id,delivered_quantity,unit_price,subtotal,discount_amount,line_total) SELECT ?,order_id,id,product_id,unit_id,quantity,round((line_total+discount_amount)/quantity,2),line_total+discount_amount,discount_amount,line_total FROM order_lines WHERE order_id=?", invoice, order);
                c.commit(); return invoice;
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    static void returns(long invoice, boolean receive, boolean damaged) throws Exception {
        long line = lookup("SELECT id FROM invoice_lines WHERE invoice_id=? ORDER BY id LIMIT 1", invoice);
        TraHangService service = new TraHangService();
        long returned = service.create(rep, SALES, Map.of("invoiceId", invoice, "note", "Trả hàng demo giảng viên",
            "lines", List.of(Map.of("invoiceLineId", line, "quantity", "1", "reason", damaged ? "DAMAGED" : "CUSTOMER_REQUEST"))));
        if (!receive) return;
        long returnLine = lookup("SELECT id FROM sales_return_lines WHERE return_id=?", returned);
        service.receive(returned, warehouse, WH, Map.of("defectWarehouseId", defect,
            "lines", List.of(Map.of("lineId", returnLine, "condition", damaged ? "DEFECT" : "GOOD"))));
        service.credit(returned, accountant, ACC, Map.of("note", "Giảm trừ hàng trả demo"));
    }

    static void verify() throws Exception {
        try (Connection c = KetNoiDB.getConnection()) {
            if (TruyVanDB.one(c,"SELECT 1 FROM inventory i FULL JOIN (SELECT warehouse_id,product_id,sum(on_hand) on_hand FROM stock_bins GROUP BY warehouse_id,product_id) b USING(warehouse_id,product_id) WHERE coalesce(i.on_hand,0)<>coalesce(b.on_hand,0) LIMIT 1") != null)
                throw new IllegalStateException("Tổng tồn và tồn lô không khớp");
            if (TruyVanDB.one(c,"SELECT 1 FROM inventory WHERE on_hand<reserved OR on_hand<0 LIMIT 1") != null)
                throw new IllegalStateException("Tồn khả dụng không hợp lệ");
            for (var u : TruyVanDB.rows(c,"SELECT password_hash FROM users")) if (!MatKhauUtil.verify("Demo@123",String.valueOf(u.get("password_hash"))))
                throw new IllegalStateException("Mật khẩu tài khoản demo không hợp lệ");
            if (number(TruyVanDB.one(c,"SELECT count(*) n FROM receivable_invoice_balances WHERE outstanding>0 AND due_date<CURRENT_DATE").get("n")) != 6)
                throw new IllegalStateException("Thiếu sáu hoá đơn quá hạn demo");
            for(String table:List.of("users","products","customers","orders","stock_receipts","stock_movements","invoices","receivable_payments","sales_returns","stock_adjustments","sales_targets"))
                System.out.println("VERIFY " + table + "=" + TruyVanDB.one(c,"SELECT count(*) n FROM " + table).get("n"));
        }
        new CongNoDaiLyService().list(lookup("SELECT id FROM users WHERE username='customer.demo'"));
        new BaoCaoQuaHanService().report(Map.of());
        System.out.println("VERIFY tồn tổng/lô, giữ chỗ, mật khẩu và công nợ: đạt");
    }
}
