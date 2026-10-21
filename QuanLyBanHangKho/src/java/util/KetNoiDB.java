package util;

import java.sql.*;

public final class KetNoiDB {

    private static final String URL = System.getenv().getOrDefault(
        "DB_URL",
        "jdbc:postgresql://localhost:5432/quanlybanhangkho"
    );
    private static final String USER = System.getenv().getOrDefault("DB_USER", "postgres");
    private static final String PASS = System.getenv().getOrDefault("DB_PASSWORD", "123456");

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("Thiếu PostgreSQL JDBC Driver trong WEB-INF/lib");
        }
    }

    private KetNoiDB() {}

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
