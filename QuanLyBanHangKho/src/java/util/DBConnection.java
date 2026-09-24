package util;
import java.sql.*;
public final class DBConnection {
    private static final String URL = System.getenv().getOrDefault("DB_URL","jdbc:postgresql://localhost:5432/quanlybanhangkho");
    private static final String USER = System.getenv().getOrDefault("DB_USER","postgres");
    private static final String PASS = System.getenv().getOrDefault("DB_PASSWORD","123456");
    private DBConnection(){}
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
