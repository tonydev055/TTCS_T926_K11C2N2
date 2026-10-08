package security;

import java.sql.*;
import java.util.*;
import util.KetNoiDB;

public final class PhanQuyen {

    private static final List<String> ROLE_ORDER = List.of(
        "ADMIN",
        "SALES_MANAGER",
        "SALES_REP",
        "WH_MANAGER",
        "WAREHOUSE",
        "ACCOUNTANT",
        "CUSTOMER"
    );

    private static final Map<String, Set<String>> DEFAULT_ROLE_PERMISSIONS = Map.of(
        "ADMIN",
        Set.of(
            "admin.users",
            "admin.roles",
            "admin.audit",
            "admin.settings",
            "catalog.read",
            "products.read",
            "products.write",
            "units.write",
            "suppliers.read",
            "suppliers.write",
            "prices.write",
            "inventory.read",
            "warehouses.manage"
        ),
        "SALES_MANAGER",
        Set.of(
            "products.read",
            "products.write",
            "products.cost",
            "prices.write",
            "customers.read",
            "orders.read",
            "orders.approve",
            "reports.sales"
        ),
        "SALES_REP",
        Set.of(
            "products.read",
            "customers.assigned",
            "orders.own",
            "payments.route",
            "targets.own"
        ),
        "WH_MANAGER",
        Set.of(
            "products.read",
            "units.write",
            "inventory.read",
            "inventory.write",
            "warehouses.manage",
            "suppliers.read",
            "suppliers.write",
            "reports.inventory"
        ),
        "WAREHOUSE",
        Set.of(
            "products.read",
            "units.write",
            "suppliers.read",
            "suppliers.write",
            "inventory.read",
            "inventory.write",
            "shipments.write"
        ),
        "ACCOUNTANT",
        Set.of(
            "products.read",
            "customers.read",
            "orders.read",
            "invoices.write",
            "payments.write",
            "receivables.read"
        ),
        "CUSTOMER",
        Set.of("products.read", "orders.self", "shipments.self", "invoices.self", "returns.self")
    );

    private static final Set<String> KNOWN_PERMISSIONS;
    private static volatile Map<String, Set<String>> configuredPermissions;

    static {
        Set<String> permissions = new TreeSet<>();
        DEFAULT_ROLE_PERMISSIONS.values().forEach(permissions::addAll);
        permissions.add("profile.self");
        KNOWN_PERMISSIONS = Collections.unmodifiableSet(permissions);
    }

    private PhanQuyen() {}

    public static boolean allows(Collection<String> roles, String permission) {
        if (permission == null || permission.isBlank()) return false;
        if (permission.equals("profile.self")) return roles
            .stream()
            .anyMatch(ROLE_ORDER::contains);
        Map<String, Set<String>> matrix = effectivePermissions();
        for (String role : roles)
            if (matrix.getOrDefault(role, Set.of()).contains(permission)) return true;
        return false;
    }

    public static Set<String> permissions(Collection<String> roles) {
        Set<String> out = new LinkedHashSet<>();
        Map<String, Set<String>> matrix = effectivePermissions();
        for (String role : roles) out.addAll(matrix.getOrDefault(role, Set.of()));
        return out;
    }

    public static Map<String, List<String>> matrix() {
        Map<String, Set<String>> effective = effectivePermissions();
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String role : ROLE_ORDER) {
            List<String> permissions = new ArrayList<>(effective.getOrDefault(role, Set.of()));
            permissions.add("profile.self");
            Collections.sort(permissions);
            out.put(role, List.copyOf(permissions));
        }
        return Collections.unmodifiableMap(out);
    }

    public static Set<String> knownPermissions() {
        return KNOWN_PERMISSIONS;
    }

    public static synchronized void updateRole(
        String role,
        Collection<String> permissions,
        long actor
    ) throws SQLException {
        if (!ROLE_ORDER.contains(role)) throw new IllegalArgumentException("Vai trò không tồn tại");
        Set<String> requested = new TreeSet<>(permissions);
        requested.remove("profile.self");
        if (!KNOWN_PERMISSIONS.containsAll(requested)) throw new IllegalArgumentException(
            "Danh sách quyền chứa giá trị không hợp lệ"
        );
        if (role.equals("ADMIN") && !requested.contains("admin.roles")) throw new IllegalArgumentException(
            "Không thể gỡ quyền chỉnh sửa phân quyền khỏi Quản trị hệ thống"
        );
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                long roleId;
                try (PreparedStatement p = c.prepareStatement("SELECT id FROM roles WHERE code=? FOR UPDATE")) {
                    p.setString(1, role);
                    try (ResultSet rs = p.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Vai trò không tồn tại");
                        roleId = rs.getLong(1);
                    }
                }
                try (PreparedStatement p = c.prepareStatement("SELECT set_config('app.actor_id',?,true)")) {
                    p.setString(1, String.valueOf(actor));
                    p.executeQuery();
                }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM role_permissions WHERE role_id=?")) {
                    p.setLong(1, roleId);
                    p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO role_permissions(role_id,permission_code) VALUES(?,?)"
                )) {
                    for (String permission : requested) {
                        p.setLong(1, roleId);
                        p.setString(2, permission);
                        p.addBatch();
                    }
                    p.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
        configuredPermissions = loadConfiguredPermissions();
    }

    private static Map<String, Set<String>> effectivePermissions() {
        Map<String, Set<String>> current = configuredPermissions;
        if (current != null) return current;
        synchronized (PhanQuyen.class) {
            if (configuredPermissions == null) {
                try {
                    configuredPermissions = loadConfiguredPermissions();
                } catch (SQLException e) {
                    configuredPermissions = immutableCopy(DEFAULT_ROLE_PERMISSIONS);
                }
            }
            return configuredPermissions;
        }
    }

    private static Map<String, Set<String>> loadConfiguredPermissions() throws SQLException {
        Map<String, Set<String>> loaded = new LinkedHashMap<>();
        for (String role : ROLE_ORDER) loaded.put(role, new LinkedHashSet<>());
        try (
            Connection c = KetNoiDB.getConnection();
            PreparedStatement p = c.prepareStatement(
                "SELECT r.code,rp.permission_code FROM roles r LEFT JOIN role_permissions rp ON rp.role_id=r.id " +
                "WHERE r.code=ANY(?) ORDER BY r.id,rp.permission_code"
            )
        ) {
            p.setArray(1, c.createArrayOf("varchar", ROLE_ORDER.toArray()));
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    String permission = rs.getString(2);
                    if (permission != null && KNOWN_PERMISSIONS.contains(permission)) {
                        loaded.get(rs.getString(1)).add(permission);
                    }
                }
            }
        }
        return immutableCopy(loaded);
    }

    private static Map<String, Set<String>> immutableCopy(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        for (String role : ROLE_ORDER) {
            copy.put(role, Collections.unmodifiableSet(new LinkedHashSet<>(source.getOrDefault(role, Set.of()))));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Quyền cần có cho một endpoint. Chỉ nhận đường dẫn đã được container chuẩn hoá
     * (servletPath + pathInfo, không có context path, tham số ";" hay "..") và so khớp
     * theo tiền tố từng đoạn, để không thể chèn tên endpoint khác vào URL để đổi quyền.
     */
    public static String permissionFor(String path, String method) {
        if (path == null || method == null) return null;
        boolean read = method.equals("GET");
        if (under(path, "/api/profile")) return "profile.self";
        if (under(path, "/api/role-permissions")) return "admin.roles";
        if (under(path, "/api/audit")) return "admin.audit";
        if (under(path, "/api/imports/users") || under(path, "/api/templates/users")) return "admin.users";
        if (under(path, "/api/imports/products") || under(path, "/api/templates/products")) return "products.write";
        if (under(path, "/api/product-units")) return read ? "products.read" : "units.write";
        if (under(path, "/api/customer-groups")) return read ? "products.read" : "prices.write";
        if (under(path, "/api/users")) return "admin.users";
        if (under(path, "/api/warehouses")) return read ? "inventory.read" : "warehouses.manage";
        if (under(path, "/api/inventory") || under(path, "/api/stock-transactions"))
            return read ? "inventory.read" : "inventory.write";
        if (under(path, "/api/suppliers")) return read ? "suppliers.read" : "suppliers.write";
        if (under(path, "/api/price-history")) return "prices.write";
        if (under(path, "/api/price-lists")) return read ? "products.read" : "prices.write";
        if (under(path, "/api/products") || under(path, "/api/categories") ||
            under(path, "/api/brands") || under(path, "/api/product-models"))
            return read ? "products.read" : "products.write";
        if (path.equals("/api/discount-policies/quote")) return "products.read";
        if (under(path, "/api/discount-policies")) return "prices.write";
        if (under(path, "/api/discounts")) return read ? "products.read" : "prices.write";
        if (under(path, "/api/customers")) return "customers.read";
        if (under(path, "/api/orders")) return "orders.read";
        if (under(path, "/api/invoices")) return "invoices.write";
        if (under(path, "/api/payments")) return "payments.write";
        if (under(path, "/api/returns")) return "returns.self";
        return null;
    }

    private static boolean under(String path, String base) {
        return path.equals(base) || path.startsWith(base + "/");
    }
}
