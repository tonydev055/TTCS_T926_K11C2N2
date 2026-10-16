package db;

import java.sql.*;
import java.util.*;

public class HoaDonDB extends CoSoDB {

    public List<Map<String, Object>> findAll() throws SQLException {
        return query("SELECT * FROM invoices ORDER BY id DESC");
    }

    public Map<String, Object> findById(long id) throws SQLException {
        var x = query("SELECT * FROM invoices WHERE id=?", id);
        return x.isEmpty() ? null : x.get(0);
    }

    public int delete(long id) throws SQLException {
        return update("DELETE FROM invoices WHERE id=?", id);
    }
}
