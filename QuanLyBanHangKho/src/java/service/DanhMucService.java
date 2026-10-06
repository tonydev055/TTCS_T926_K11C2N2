package service;

import db.TruyVanDB;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import util.*;

public class DanhMucService {

    public static final Set<String> TABLES = Set.of(
        "products",
        "brands",
        "product_models",
        "categories",
        "product_units",
        "suppliers",
        "customer_groups",
        "price_lists"
    );

    public static String text(Map<String, Object> data, String key, boolean required, int max) {
        String x = Objects.toString(data.get(key), "").trim();
        if ((required && x.isEmpty()) || x.length() > max) throw new IllegalArgumentException(
            "Trường " + key + " không hợp lệ (tối đa " + max + " ký tự)"
        );
        return x;
    }

    public static long integer(Map<String, Object> data, String key) {
        try {
            return new BigDecimal(Objects.toString(data.get(key), "0")).longValueExact();
        } catch (Exception e) {
            throw new IllegalArgumentException("Trường " + key + " phải là số nguyên");
        }
    }

    public static BigDecimal decimal(
        Map<String, Object> data,
        String key,
        boolean positive,
        int scale
    ) {
        try {
            BigDecimal v = new BigDecimal(Objects.toString(data.get(key), ""));
            if (
                v.signum() < (positive ? 1 : 0) ||
                v.scale() > scale ||
                v.precision() - v.scale() > 12
            ) throw new Exception();
            return v;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Trường " +
                    key +
                    " phải là số " +
                    (positive ? "dương" : "không âm") +
                    ", tối đa " +
                    scale +
                    " chữ số thập phân"
            );
        }
    }

    public static boolean active(Map<String, Object> data) {
        Object v = data.getOrDefault("active", true);
        if (!(v instanceof Boolean)) throw new IllegalArgumentException("Trạng thái không hợp lệ");
        return (Boolean) v;
    }

    public static String phone(String value, boolean required) {
        String p = value.trim().replaceAll("[ .-]", "");
        if (p.isEmpty() && !required) return p;
        if (!p.matches("(?:0|\\+84)[35789][0-9]{8}")) throw new IllegalArgumentException(
            "Số điện thoại Việt Nam không hợp lệ"
        );
        return p;
    }

    public static void begin(Connection c, long actor) throws SQLException {
        c.setAutoCommit(false);
        TruyVanDB.rows(c, "SELECT pg_advisory_xact_lock(260902)");
        TruyVanDB.rows(c, "SELECT set_config('app.actor_id',?,true)", String.valueOf(actor));
    }

    public static String table(String endpoint) {
        String t = endpoint.replace('-', '_');
        if (!TABLES.contains(t)) throw new IllegalArgumentException("Chức năng không tồn tại");
        return t;
    }

    public List<Map<String, Object>> list(
        Connection c,
        String table,
        boolean cost,
        boolean customer,
        long actor
    ) throws SQLException {
        var query = listQuery(table, cost, customer, actor);
        var rows = TruyVanDB.rows(c, query.sql(), query.params());
        addPriceLines(c, table, customer, rows);
        addProductAttributes(c, table, rows);
        return rows;
    }

    private record ListQuery(String sql, Object[] params) {}

    private ListQuery listQuery(String table, boolean cost, boolean customer, long actor) {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("Chức năng không tồn tại");
        if (table.equals("products")) return new ListQuery(
            "SELECT p.id,p.sku,p.name,p.category_id,c.name category_name,p.base_unit,p.packaging,p.active,p.updated_at,p.model_id,p.condition,m.name model_name,m.brand_id,b.name brand_name,(p.image IS NOT NULL) has_image" +
                (cost ? ",p.cost_price" : "") +
                " FROM products p JOIN categories c ON c.id=p.category_id LEFT JOIN product_models m ON m.id=p.model_id LEFT JOIN brands b ON b.id=m.brand_id " +
                (customer ? "WHERE p.active=true AND (m.id IS NULL OR m.active) " : "") +
                "ORDER BY p.id DESC", new Object[] {}
        );
        if (table.equals("product_units")) return new ListQuery(
            "SELECT u.*,p.sku,p.name product_name FROM product_units u JOIN products p ON p.id=u.product_id WHERE u.active=true ORDER BY p.sku,u.factor", new Object[] {}
        );
        if (table.equals("price_lists")) {
            return new ListQuery(
                "SELECT p.*,g.name group_name,EXISTS(SELECT 1 FROM orders o WHERE o.price_list_id=p.id) locked FROM price_lists p JOIN customer_groups g ON g.id=p.customer_group_id " +
                    (customer
                        ? "WHERE p.active AND CURRENT_DATE BETWEEN p.valid_from AND p.valid_to AND p.customer_group_id=(SELECT customer_group_id FROM users WHERE id=?) "
                        : "") +
                    "ORDER BY p.id DESC",
                customer ? new Object[] { actor } : new Object[] {}
            );
        }
        if (customer && table.equals("customer_groups")) return new ListQuery(
            "SELECT g.* FROM customer_groups g JOIN users u ON u.customer_group_id=g.id WHERE u.id=?",
            new Object[] { actor }
        );
        if (table.equals("product_models")) return new ListQuery(
            "SELECT m.*,c.name category_name,b.name brand_name,(SELECT count(*) FROM products p WHERE p.model_id=m.id) variant_count " +
            "FROM product_models m JOIN categories c ON c.id=m.category_id JOIN brands b ON b.id=m.brand_id " +
            (customer ? "WHERE m.active AND EXISTS(SELECT 1 FROM products p WHERE p.model_id=m.id AND p.active) " : "") +
            "ORDER BY m.id DESC", new Object[] {}
        );
        if (table.equals("brands")) return new ListQuery(
            "SELECT * FROM brands " + (customer ? "WHERE active " : "") + "ORDER BY name,id", new Object[] {}
        );
        return new ListQuery("SELECT * FROM " + table + " ORDER BY id DESC", new Object[] {});
    }

    private void addPriceLines(Connection c, String table, boolean customer, List<Map<String, Object>> rows) throws SQLException {
        if (table.equals("price_lists")) {
            for (var p : rows)
                p.put(
                    "lines",
                    TruyVanDB.rows(
                        c,
                        "SELECT l.product_id,p.sku,p.name product_name,p.base_unit,l.price" +
                            (customer ? "" : ",l.floor_price") +
                            " FROM price_list_lines l JOIN products p ON p.id=l.product_id WHERE l.price_list_id=? ORDER BY p.sku",
                        p.get("id")
                    )
                );
        }
    }

    public Map<String, Object> page(Connection c, String table, boolean cost, boolean customer,
        long actor, int page, int size, String search, String status) throws SQLException {
        return page(c, table, cost, customer, actor, page, size, search, status, null, null, null);
    }

    public Map<String, Object> page(Connection c, String table, boolean cost, boolean customer,
        long actor, int page, int size, String search, String status, Long category, Long brand, Long model) throws SQLException {
        if (page < 1 || size < 1 || size > 100) throw new IllegalArgumentException("Phân trang không hợp lệ");
        if (!Set.of("", "true", "false").contains(status)) throw new IllegalArgumentException("Trạng thái không hợp lệ");
        var query = listQuery(table, cost, customer, actor);
        String columns = switch (table) {
            case "products" -> "sku,name,category_name,brand_name,model_name,base_unit,packaging";
            case "product_models" -> "code,name,category_name,brand_name";
            case "product_units" -> "sku,product_name,name,factor";
            case "suppliers" -> "code,name,tax_code,contact_name,phone,payment_terms";
            case "price_lists" -> "name,group_name,valid_from,valid_to,version";
            default -> "code,name";
        };
        List<Object> params = new ArrayList<>(Arrays.asList(query.params()));
        String source = " FROM (" + query.sql() + ") records WHERE strpos(lower(concat_ws(' '," + columns + ")), lower(?)) > 0";
        params.add(search.trim());
        if (!status.isEmpty() && Set.of("products", "product_models", "brands", "suppliers", "price_lists", "product_units").contains(table)) {
            source += " AND active=?";
            params.add(Boolean.valueOf(status));
        }
        if (Set.of("products", "product_models").contains(table)) {
            if (category != null) {
                source += " AND category_id IN (WITH RECURSIVE descendants AS (SELECT id FROM categories WHERE id=? UNION SELECT c.id FROM categories c JOIN descendants d ON c.parent_id=d.id) SELECT id FROM descendants)";
                params.add(category);
            }
            if (brand != null) { source += " AND brand_id=?"; params.add(brand); }
            if (model != null && table.equals("products")) { source += " AND model_id=?"; params.add(model); }
        }
        long total = ((Number) TruyVanDB.one(c, "SELECT count(*) total" + source, params.toArray()).get("total")).longValue();
        int lastPage = (int) Math.max(1, (total + size - 1) / size);
        page = Math.min(page, lastPage);
        String order = table.equals("product_units") ? "sku,factor,id" : "id DESC";
        params.add(size);
        params.add((long) (page - 1) * size);
        var items = TruyVanDB.rows(c, "SELECT *" + source + " ORDER BY " + order + " LIMIT ? OFFSET ?", params.toArray());
        addPriceLines(c, table, customer, items);
        addProductAttributes(c, table, items);
        return Map.of("items", items, "total", total, "page", page, "size", size);
    }

    private void addProductAttributes(Connection c, String table, List<Map<String, Object>> rows) throws SQLException {
        if (!table.equals("products") || rows.isEmpty()) return;
        Map<Long, Map<String, String>> attributes = new HashMap<>();
        for (var row : rows) {
            var values = new LinkedHashMap<String, String>();
            attributes.put(TruyVanDB.id(row), values);
            row.put("attributes", values);
        }
        String placeholders = String.join(",", Collections.nCopies(rows.size(), "?"));
        for (var value : TruyVanDB.rows(c, "SELECT product_id,name,value FROM product_attributes WHERE product_id IN (" + placeholders + ") ORDER BY name",
            rows.stream().map(row -> row.get("id")).toArray())) {
            attributes.get(((Number) value.get("product_id")).longValue()).put(value.get("name").toString(), value.get("value").toString());
        }
    }

    public static Map<String, String> validateAttributes(Object raw) {
        if (!(raw instanceof Map<?, ?> values) || values.size() > 30) throw new IllegalArgumentException("Thuộc tính phải có tối đa 30 cặp tên và giá trị");
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        for (var entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String name) || !(entry.getValue() instanceof String value)) throw new IllegalArgumentException("Tên và giá trị thuộc tính phải là văn bản");
            name = name.trim(); value = value.trim();
            if (name.isEmpty() || name.length() > 60 || value.isEmpty() || value.length() > 250 || !names.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Thuộc tính bị trùng hoặc không hợp lệ");
            result.put(name, value);
        }
        return result;
    }

    public long save(Connection c, String table, Long id, Map<String, Object> data, boolean cost)
        throws SQLException {
        Map<String, Object> old =
            id == null ? null : TruyVanDB.one(c, "SELECT * FROM " + table + " WHERE id=? FOR UPDATE", id);
        if (id != null && old == null) throw new IllegalArgumentException("Không tìm thấy bản ghi");
        Map<String, Object> values = new LinkedHashMap<>();
        switch (table) {
            case "products" -> {
                values.put("sku", text(data, "sku", true, 80).toUpperCase(Locale.ROOT));
                values.put("name", text(data, "name", true, 200));
                Long modelId = data.containsKey("model_id")
                    ? (data.get("model_id") == null ? null : integer(data, "model_id"))
                    : old == null || old.get("model_id") == null ? null : ((Number) old.get("model_id")).longValue();
                var model = modelId == null ? null : require(c, "product_models", modelId);
                long category = model == null ? integer(data, "category_id") : ((Number) model.get("category_id")).longValue();
                if (model != null && !Boolean.TRUE.equals(model.get("active")) && (old == null || !Objects.equals(old.get("model_id"), modelId))) throw new IllegalArgumentException("Mẫu sản phẩm đã ngừng hoạt động");
                if (old != null && old.get("model_id") != null && !Objects.equals(old.get("model_id"), modelId)) throw new IllegalArgumentException("Không chuyển SKU đã gắn mẫu sang mẫu khác; hãy tạo SKU mới");
                values.put("model_id", modelId);
                String condition = data.containsKey("condition") ? text(data, "condition", true, 20)
                    : old == null ? "NEW" : old.get("condition").toString();
                if (!Set.of("NEW", "USED", "DISPLAY").contains(condition)) throw new IllegalArgumentException("Tình trạng hàng không hợp lệ");
                values.put("condition", condition);
                if (data.containsKey("attributes")) validateAttributes(data.get("attributes"));
                require(c, "categories", category);
                values.put("category_id", category);
                String base = text(data, "base_unit", true, 40);
                if (
                    old != null && !base.equals(old.get("base_unit"))
                ) throw new IllegalArgumentException(
                    "Không đổi đơn vị cơ sở sau khi tạo SKU; hãy thêm đơn vị quy đổi"
                );
                values.put("base_unit", base);
                values.put("packaging", text(data, "packaging", false, 255));
                values.put("active", active(data));
                if (data.containsKey("cost_price") && !cost) throw new SecurityException(
                    "Chỉ Quản lý kinh doanh được sửa giá vốn"
                );
                if (cost) values.put("cost_price", decimal(data, "cost_price", false, 2));
                values.put("updated_at", Timestamp.valueOf(java.time.LocalDateTime.now()));
            }
            case "brands" -> {
                values.put("code", text(data, "code", true, 40).toUpperCase(Locale.ROOT));
                values.put("name", text(data, "name", true, 150));
                values.put("active", active(data));
            }
            case "product_models" -> {
                long category = integer(data, "category_id"), brand = integer(data, "brand_id");
                require(c, "categories", category);
                var brandRow = require(c, "brands", brand);
                if (!Boolean.TRUE.equals(brandRow.get("active")) && (old == null || !Objects.equals(old.get("brand_id"), brand))) throw new IllegalArgumentException("Thương hiệu đã ngừng hoạt động");
                values.put("code", text(data, "code", true, 80).toUpperCase(Locale.ROOT));
                values.put("name", text(data, "name", true, 200));
                values.put("category_id", category);
                values.put("brand_id", brand);
                values.put("active", active(data));
            }
            case "categories" -> {
                values.put("code", text(data, "code", true, 40).toUpperCase(Locale.ROOT));
                values.put("name", text(data, "name", true, 150));
                Long parent =
                    data.get("parent_id") == null ||
                    Objects.toString(data.get("parent_id"), "").isBlank()
                        ? null
                        : integer(data, "parent_id");
                if (parent != null) {
                    require(c, "categories", parent);
                    Set<Long> visited = new HashSet<>();
                    Long cursor = parent;
                    while (cursor != null) {
                        if (
                            Objects.equals(cursor, id) || !visited.add(cursor)
                        ) throw new IllegalArgumentException("Nhóm cha tạo vòng lặp");
                        Object next = TruyVanDB.one(
                            c,
                            "SELECT parent_id FROM categories WHERE id=?",
                            cursor
                        ).get("parent_id");
                        cursor = next == null ? null : ((Number) next).longValue();
                    }
                }
                values.put("parent_id", parent);
            }
            case "suppliers" -> {
                values.put("code", text(data, "code", true, 40).toUpperCase(Locale.ROOT));
                values.put("name", text(data, "name", true, 200));
                values.put("tax_code", text(data, "tax_code", true, 40));
                values.put("contact_name", text(data, "contact_name", true, 150));
                values.put("phone", phone(text(data, "phone", false, 20), false));
                values.put("payment_terms", text(data, "payment_terms", true, 2000));
                values.put("active", active(data));
            }
            case "customer_groups" -> {
                values.put("code", text(data, "code", true, 40).toUpperCase(Locale.ROOT));
                values.put("name", text(data, "name", true, 150));
            }
            case "product_units" -> {
                long product = integer(data, "product_id");
                var p = require(c, "products", product);
                String name = text(data, "name", true, 40);
                BigDecimal factor = decimal(data, "factor", true, 6);
                if (
                    old != null &&
                    (!Boolean.TRUE.equals(old.get("active")) ||
                        ((Number) old.get("product_id")).longValue() != product)
                ) throw new IllegalArgumentException("Đơn vị đã thay đổi; hãy tải lại");
                if (
                    name.equalsIgnoreCase(String.valueOf(p.get("base_unit"))) &&
                    factor.compareTo(BigDecimal.ONE) != 0
                ) throw new IllegalArgumentException("Đơn vị cơ sở phải có hệ số bằng 1");
                if (
                    old != null &&
                    String.valueOf(old.get("name")).equalsIgnoreCase(
                        String.valueOf(p.get("base_unit"))
                    )
                ) throw new IllegalArgumentException("Không sửa đơn vị cơ sở");
                if (old != null) {
                    TruyVanDB.update(c, "UPDATE product_units SET active=false WHERE id=?", id);
                    id = null;
                }
                values.put("product_id", product);
                values.put("name", name);
                values.put("factor", factor);
            }
            case "price_lists" -> {
                return savePrices(c, id, data);
            }
            default -> throw new IllegalArgumentException("Chức năng không tồn tại");
        }
        long saved = write(c, table, id, values);
        if (table.equals("product_models")) TruyVanDB.update(c, "UPDATE products SET category_id=?,updated_at=CURRENT_TIMESTAMP WHERE model_id=? AND category_id<>?", values.get("category_id"), saved, values.get("category_id"));
        if (table.equals("products") && data.containsKey("attributes")) {
            var attributes = validateAttributes(data.get("attributes"));
            TruyVanDB.update(c, "DELETE FROM product_attributes WHERE product_id=?", saved);
            for (var attribute : attributes.entrySet()) TruyVanDB.update(c, "INSERT INTO product_attributes(product_id,name,value) VALUES(?,?,?)", saved, attribute.getKey(), attribute.getValue());
        }
        if (table.equals("products") && id == null) TruyVanDB.update(
            c,
            "INSERT INTO product_units(product_id,name,factor) VALUES(?,?,1)",
            saved,
            values.get("base_unit")
        );
        return saved;
    }

    public static Map<String, Object> require(Connection c, String table, long id)
        throws SQLException {
        var row = TruyVanDB.one(c, "SELECT * FROM " + table + " WHERE id=?", id);
        if (row == null) throw new IllegalArgumentException("Không tìm thấy " + table + " #" + id);
        return row;
    }

    public static long write(Connection c, String table, Long id, Map<String, Object> values)
        throws SQLException {
        List<Object> args = new ArrayList<>(values.values());
        if (id == null) {
            String sql =
                "INSERT INTO " +
                table +
                " (" +
                String.join(",", values.keySet()) +
                ") VALUES (" +
                String.join(",", Collections.nCopies(values.size(), "?")) +
                ") RETURNING id";
            return TruyVanDB.id(TruyVanDB.one(c, sql, args.toArray()));
        }
        args.add(id);
        TruyVanDB.update(
            c,
            "UPDATE " +
                table +
                " SET " +
                String.join(
                    ",",
                    values
                        .keySet()
                        .stream()
                        .map(k -> k + "=?")
                        .toList()
                ) +
                " WHERE id=?",
            args.toArray()
        );
        return id;
    }

    @SuppressWarnings("unchecked")
    private long savePrices(Connection c, Long id, Map<String, Object> data) throws SQLException {
        if (
            id != null &&
            TruyVanDB.one(c, "SELECT id FROM orders WHERE price_list_id=? LIMIT 1", id) != null
        ) throw new IllegalArgumentException("Bảng giá đã phát sinh đơn; hãy tạo phiên bản mới");
        long group = integer(data, "customer_group_id");
        require(c, "customer_groups", group);
        LocalDate from, to;
        try {
            from = LocalDate.parse(text(data, "valid_from", true, 10));
            to = LocalDate.parse(text(data, "valid_to", true, 10));
        } catch (Exception e) {
            throw new IllegalArgumentException("Ngày hiệu lực không hợp lệ");
        }
        if (to.isBefore(from)) throw new IllegalArgumentException(
            "Ngày kết thúc phải từ ngày bắt đầu trở đi"
        );
        Long previous = data.get("previous_id") == null ? null : integer(data, "previous_id");
        int version = 1;
        if (id != null) {
            var old = require(c, "price_lists", id);
            previous =
                old.get("previous_id") == null
                    ? null
                    : ((Number) old.get("previous_id")).longValue();
            version = ((Number) old.get("version")).intValue();
        } else if (previous != null) {
            var old = require(c, "price_lists", previous);
            if (
                ((Number) old.get("customer_group_id")).longValue() != group
            ) throw new IllegalArgumentException("Phiên bản mới phải cùng nhóm khách hàng");
            version =
                (
                    (Number) TruyVanDB.one(
                        c,
                        "WITH RECURSIVE chain AS (SELECT id,version FROM price_lists WHERE id=? UNION ALL SELECT p.id,p.version FROM price_lists p JOIN chain c ON p.previous_id=c.id) SELECT max(version) v FROM chain",
                        previous
                    ).get("v")
                ).intValue() + 1;
        }
        boolean enabled = active(data);
        if (
            enabled &&
            TruyVanDB.one(
                c,
                "SELECT id FROM price_lists WHERE customer_group_id=? AND active AND id<>? AND valid_from<=? AND valid_to>=? LIMIT 1",
                group,
                id == null ? 0 : id,
                java.sql.Date.valueOf(to),
                java.sql.Date.valueOf(from)
            ) != null
        ) throw new IllegalArgumentException(
            "Thời gian trùng bảng giá đang áp dụng của nhóm này. Hãy điều chỉnh ngày hoặc ngừng áp dụng bảng cũ"
        );
        Object raw = data.get("lines");
        if (
            !(raw instanceof List<?> lines) || lines.isEmpty() || lines.size() > 5000
        ) throw new IllegalArgumentException("Bảng giá phải có từ 1 đến 5000 dòng");
        Set<Long> products = new HashSet<>();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Object row : lines) {
            if (!(row instanceof Map)) throw new IllegalArgumentException("Dòng giá không hợp lệ");
            Map<String, Object> line = (Map<String, Object>) row;
            long product = integer(line, "product_id");
            require(c, "products", product);
            if (!products.add(product)) throw new IllegalArgumentException(
                "Sản phẩm bị lặp trong bảng giá"
            );
            BigDecimal price = decimal(line, "price", false, 2),
                floor = decimal(line, "floor_price", false, 2);
            if (floor.compareTo(price) > 0) throw new IllegalArgumentException(
                "Giá sàn không được lớn hơn giá bán"
            );
            entries.add(Map.of("product_id", product, "price", price, "floor_price", floor));
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", text(data, "name", true, 150));
        values.put("customer_group_id", group);
        values.put("valid_from", java.sql.Date.valueOf(from));
        values.put("valid_to", java.sql.Date.valueOf(to));
        values.put("active", enabled);
        values.put("previous_id", previous);
        values.put("version", version);
        long saved = write(c, "price_lists", id, values);
        if (id != null) TruyVanDB.update(c, "DELETE FROM price_list_lines WHERE price_list_id=?", id);
        for (var line : entries)
            TruyVanDB.update(
                c,
                "INSERT INTO price_list_lines(price_list_id,product_id,price,floor_price) VALUES(?,?,?,?)",
                saved,
                line.get("product_id"),
                line.get("price"),
                line.get("floor_price")
            );
        return saved;
    }

    public void delete(Connection c, String table, long id) throws SQLException {
        var old = require(c, table, id);
        if (
            table.equals("categories") &&
            (TruyVanDB.one(c, "SELECT id FROM products WHERE category_id=? LIMIT 1", id) != null ||
                TruyVanDB.one(c, "SELECT id FROM categories WHERE parent_id=? LIMIT 1", id) != null)
        ) throw new IllegalArgumentException("Nhóm còn sản phẩm hoặc nhóm con, không thể xóa");
        if (table.equals("product_units")) {
            var p = require(c, "products", ((Number) old.get("product_id")).longValue());
            if (
                old.get("name").toString().equalsIgnoreCase(p.get("base_unit").toString())
            ) throw new IllegalArgumentException("Không xóa đơn vị cơ sở");
            TruyVanDB.update(c, "UPDATE product_units SET active=false WHERE id=?", id);
            return;
        }
        TruyVanDB.update(c, "DELETE FROM " + table + " WHERE id=?", id);
    }

    public static String error(Exception e) {
        if (e instanceof SQLException sql) {
            return switch (Objects.toString(sql.getSQLState(), "")) {
                case "23505" -> "Mã đã tồn tại. Vui lòng dùng mã khác";
                case "23503" -> "Dữ liệu đã được sử dụng, không thể xóa; hãy chuyển sang ngừng hoạt động";
                case "23514", "23502", "22003" -> "Dữ liệu không đáp ứng điều kiện hợp lệ";
                default -> "Không thể xử lý dữ liệu. Vui lòng thử lại";
            };
        }
        return e.getMessage() == null ? "Không thể xử lý yêu cầu" : e.getMessage();
    }
}
