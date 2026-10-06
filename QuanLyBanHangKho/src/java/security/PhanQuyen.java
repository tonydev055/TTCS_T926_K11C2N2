package security;

import java.util.*;

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

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
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

    private PhanQuyen() {}

    public static boolean allows(Collection<String> roles, String permission) {
        if (permission == null || permission.isBlank()) return false;
        if (permission.equals("profile.self")) return roles
            .stream()
            .anyMatch(ROLE_PERMISSIONS::containsKey);
        for (String role : roles)
            if (ROLE_PERMISSIONS.getOrDefault(role, Set.of()).contains(permission)) return true;
        return false;
    }

    public static Set<String> permissions(Collection<String> roles) {
        Set<String> out = new LinkedHashSet<>();
        for (String role : roles) out.addAll(ROLE_PERMISSIONS.getOrDefault(role, Set.of()));
        return out;
    }

    /** Read-only snapshot used by the administration permission matrix. */
    public static Map<String, List<String>> matrix() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String role : ROLE_ORDER) {
            List<String> permissions = new ArrayList<>(ROLE_PERMISSIONS.getOrDefault(role, Set.of()));
            permissions.add("profile.self");
            Collections.sort(permissions);
            out.put(role, List.copyOf(permissions));
        }
        return Collections.unmodifiableMap(out);
    }

    public static String permissionFor(String uri, String method) {
        boolean read = method.equals("GET");
        if (uri.contains("/api/profile")) return "profile.self";
        if (uri.contains("/api/role-permissions")) return "admin.roles";
        if (uri.contains("/api/audit")) return "admin.audit";
        if (
            uri.contains("/api/imports/users") || uri.contains("/api/templates/users")
        ) return "admin.users";
        if (
            uri.contains("/api/imports/products") || uri.contains("/api/templates/products")
        ) return "products.write";
        if (uri.contains("/api/product-units")) return read ? "products.read" : "units.write";
        if (uri.contains("/api/customer-groups")) return read ? "products.read" : "prices.write";
        if (uri.contains("/api/users")) return "admin.users";
        if (uri.contains("/api/warehouses")) return method.equals("GET")
            ? "inventory.read"
            : "warehouses.manage";
        if (
            uri.contains("/api/inventory") || uri.contains("/api/stock-transactions")
        ) return method.equals("GET") ? "inventory.read" : "inventory.write";
        if (uri.contains("/api/suppliers")) return read ? "suppliers.read" : "suppliers.write";
        if (uri.contains("/api/price-lists")) return read ? "products.read" : "prices.write";
        if (uri.contains("/api/products") || uri.contains("/api/categories") || uri.contains("/api/brands") || uri.contains("/api/product-models")) return read
            ? "products.read"
            : "products.write";
        if (uri.contains("/api/discounts")) return read ? "products.read" : "prices.write";
        if (uri.contains("/api/customers")) return "customers.read";
        if (uri.contains("/api/orders")) return "orders.read";
        if (uri.contains("/api/invoices")) return "invoices.write";
        if (uri.contains("/api/payments")) return "payments.write";
        if (uri.contains("/api/returns")) return "returns.self";
        return null;
    }
}
