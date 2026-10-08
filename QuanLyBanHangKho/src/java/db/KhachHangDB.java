package db;

import java.sql.*;
import java.util.*;

/** SQL cho hồ sơ đại lý (bảng customers). Service quản lý giao dịch và phạm vi dữ liệu. */
public class KhachHangDB {

    private static final String SELECT =
        "SELECT k.id,k.code,k.name,k.tax_code,k.customer_group_id,g.code group_code,g.name group_name," +
        "k.region,k.phone,k.sales_rep_id,u.full_name sales_rep_name,u.email sales_rep_email," +
        "k.status,k.created_at,k.updated_at " +
        "FROM customers k JOIN customer_groups g ON g.id=k.customer_group_id " +
        "LEFT JOIN users u ON u.id=k.sales_rep_id ";

    /** Bộ lọc danh sách; {@code onlyRep} giới hạn đại lý của một nhân viên phụ trách. */
    public record Filter(String search, String region, Long groupId, Long salesRepId, String status, Long onlyRep) {}

    private record Where(String sql, List<Object> params) {}

    private Where where(Filter f) {
        StringBuilder sql = new StringBuilder("WHERE TRUE ");
        List<Object> params = new ArrayList<>();
        if (f.onlyRep() != null) { sql.append("AND k.sales_rep_id=? "); params.add(f.onlyRep()); }
        if (!f.search().isEmpty()) {
            String like = "%" + f.search().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            sql.append("AND (lower(k.code) LIKE ? OR lower(k.name) LIKE ? OR k.phone LIKE ? OR k.tax_code LIKE ?) ");
            Collections.addAll(params, like, like, like, like);
        }
        if (!f.region().isEmpty()) { sql.append("AND k.region=? "); params.add(f.region()); }
        if (f.groupId() != null) { sql.append("AND k.customer_group_id=? "); params.add(f.groupId()); }
        if (f.salesRepId() != null) { sql.append("AND k.sales_rep_id=? "); params.add(f.salesRepId()); }
        if (!f.status().isEmpty()) { sql.append("AND k.status=? "); params.add(f.status()); }
        return new Where(sql.toString(), params);
    }

    public List<Map<String, Object>> list(Connection c, Filter f, int page, int size) throws SQLException {
        Where w = where(f);
        List<Object> params = new ArrayList<>(w.params());
        params.add(size);
        params.add((page - 1) * size);
        return TruyVanDB.rows(c, SELECT + w.sql() + "ORDER BY k.code LIMIT ? OFFSET ?", params.toArray());
    }

    public long count(Connection c, Filter f) throws SQLException {
        Where w = where(f);
        var row = TruyVanDB.one(c, "SELECT count(*) total FROM customers k " + w.sql(), w.params().toArray());
        return ((Number) row.get("total")).longValue();
    }

    public Map<String, Object> find(Connection c, long id) throws SQLException {
        return TruyVanDB.one(c, SELECT + "WHERE k.id=?", id);
    }

    public Map<String, Object> findForUpdate(Connection c, long id) throws SQLException {
        return TruyVanDB.one(c, "SELECT * FROM customers WHERE id=? FOR UPDATE", id);
    }

    public boolean hasTransactions(Connection c, long id) throws SQLException {
        return TruyVanDB.one(c, "SELECT 1 FROM orders WHERE agent_id=? LIMIT 1", id) != null;
    }

    /** Bảng giá đang hiệu lực hôm nay của nhóm khách hàng (bản mới nhất nếu có nhiều phiên bản). */
    public Map<String, Object> currentPriceList(Connection c, long groupId) throws SQLException {
        return TruyVanDB.one(
            c,
            "SELECT id,name,version,valid_from,valid_to FROM price_lists WHERE customer_group_id=? AND active " +
                "AND CURRENT_DATE BETWEEN valid_from AND valid_to ORDER BY valid_from DESC,version DESC,id DESC LIMIT 1",
            groupId
        );
    }

    public boolean groupExists(Connection c, long groupId) throws SQLException {
        return TruyVanDB.one(c, "SELECT 1 FROM customer_groups WHERE id=?", groupId) != null;
    }

    /** Người dùng đang hoạt động có vai trò Nhân viên kinh doanh. */
    public boolean isActiveSalesRep(Connection c, long userId) throws SQLException {
        return TruyVanDB.one(
            c,
            "SELECT 1 FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id " +
                "WHERE u.id=? AND u.active AND r.code='SALES_REP'",
            userId
        ) != null;
    }

    public long insert(Connection c, Map<String, Object> v) throws SQLException {
        var row = TruyVanDB.one(
            c,
            "INSERT INTO customers(code,name,tax_code,customer_group_id,region,phone,sales_rep_id,status) " +
                "VALUES(?,?,?,?,?,?,?,?) RETURNING id",
            v.get("code"), v.get("name"), v.get("tax_code"), v.get("customer_group_id"),
            v.get("region"), v.get("phone"), v.get("sales_rep_id"), v.get("status")
        );
        return TruyVanDB.id(row);
    }

    public void update(Connection c, long id, Map<String, Object> v) throws SQLException {
        TruyVanDB.update(
            c,
            "UPDATE customers SET code=?,name=?,tax_code=?,customer_group_id=?,region=?,phone=?," +
                "sales_rep_id=?,status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
            v.get("code"), v.get("name"), v.get("tax_code"), v.get("customer_group_id"),
            v.get("region"), v.get("phone"), v.get("sales_rep_id"), v.get("status"), id
        );
    }

    public void delete(Connection c, long id) throws SQLException {
        TruyVanDB.update(c, "DELETE FROM customers WHERE id=?", id);
    }

    public List<Map<String, Object>> groups(Connection c) throws SQLException {
        return TruyVanDB.rows(c, "SELECT id,code,name FROM customer_groups ORDER BY name");
    }

    public List<Map<String, Object>> salesReps(Connection c, Long onlyUser) throws SQLException {
        return TruyVanDB.rows(
            c,
            "SELECT DISTINCT u.id,u.full_name,u.email,u.territory FROM users u " +
                "JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id " +
                "WHERE r.code='SALES_REP' AND u.active AND (?::bigint IS NULL OR u.id=?) ORDER BY u.full_name",
            onlyUser, onlyUser
        );
    }

    public List<String> regions(Connection c, Long onlyRep) throws SQLException {
        List<String> out = new ArrayList<>();
        for (var row : TruyVanDB.rows(
            c,
            "SELECT DISTINCT region FROM customers WHERE (?::bigint IS NULL OR sales_rep_id=?) ORDER BY region",
            onlyRep, onlyRep
        )) out.add(String.valueOf(row.get("region")));
        return out;
    }
}
