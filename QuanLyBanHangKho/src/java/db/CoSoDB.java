package db;

import java.sql.*;
import java.util.*;
import util.KetNoiDB;

public class CoSoDB {

    public List<Map<String, Object>> query(String sql, Object... args) throws SQLException {
        try (
            Connection c = KetNoiDB.getConnection();
            PreparedStatement p = c.prepareStatement(sql)
        ) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            try (ResultSet r = p.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                ResultSetMetaData m = r.getMetaData();
                while (r.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= m.getColumnCount(); i++) row.put(
                        m.getColumnLabel(i),
                        r.getObject(i)
                    );
                    out.add(row);
                }
                return out;
            }
        }
    }

    public int update(String sql, Object... args) throws SQLException {
        try (
            Connection c = KetNoiDB.getConnection();
            PreparedStatement p = c.prepareStatement(sql)
        ) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            return p.executeUpdate();
        }
    }
}
