package service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Creates or removes the disposable PostgreSQL database used by S7-01 integration tests. */
public final class S7TestDatabase {
    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String TEST_DB = System.getenv("S7_TEST_DB");
    private static final List<String> MIGRATIONS = List.of(
        "sprint1.sql", "sprint2.sql", "role-permissions.sql", "sprint3.sql",
        "sprint3-price-history.sql", "sprint3-discount-audit.sql", "sprint3-customers.sql",
        "sprint3-delivery-addresses.sql", "sprint3-orders.sql", "sprint3-credit-limit.sql",
        "sprint3-customer-assignment.sql", "sprint3-customer-lock.sql", "sprint3-customer-search.sql",
        "sprint4-order-lifecycle.sql", "sprint4-order-pricing.sql", "sprint4-credit-check.sql",
        "sprint4-stock-availability.sql", "sprint4-order-approval.sql", "sprint4-dealer-portal.sql",
        "sprint5-warehouses.sql", "sprint5-stock-receipts.sql", "sprint5-reservation-expiry.sql",
        "sprint5-order-tracking.sql", "sprint5-stock-counts.sql", "sprint5-transfers.sql",
        "sprint5-low-stock.sql", "sprint6-pick-lists.sql", "sprint6-shipments.sql",
        "sprint6-delivery-confirmation.sql", "sprint6-delivery-trips.sql", "sprint6-lots.sql",
        "sprint7-sales-invoices.sql", "sprint7-payments.sql", "smtp.sql", "sprint7-payment-reminders.sql"
    );

    public static void main(String[] args) throws Exception {
        if(TEST_DB==null||!TEST_DB.matches("khoflow_s7_test_[0-9]{14}"))throw new IllegalStateException("Cần S7_TEST_DB riêng cho lượt chạy, dạng khoflow_s7_test_YYYYMMDDhhmmss");
        Class.forName("org.postgresql.Driver");
        String action = args.length == 0 ? "create" : args[0];
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, "postgres", "123456");
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='" + TEST_DB + "' AND pid<>pg_backend_pid()");
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB);
            if ("create".equals(action)) {
                statement.execute("CREATE DATABASE " + TEST_DB);
            }
        }
        if ("create".equals(action)) {
            try (Connection connection = DriverManager.getConnection(ADMIN_URL.substring(0,ADMIN_URL.lastIndexOf('/')+1) + TEST_DB, "postgres", "123456");
                 Statement statement = connection.createStatement()) {
                for (String migration : MIGRATIONS) {
                    statement.execute(Files.readString(Path.of("database", migration)));
                    System.out.println("Applied " + migration);
                }
                statement.execute("TRUNCATE invoice_number_counters");
            }
        }
        System.out.println("S7 test database: " + action + " complete");
    }
}

