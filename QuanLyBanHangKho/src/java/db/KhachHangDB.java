package db;

import java.sql.*;
import java.util.*;

/** SQL cho hồ sơ đại lý (bảng customers). Service quản lý giao dịch và phạm vi dữ liệu. */
public class KhachHangDB {

    private static final String SELECT =
        "SELECT k.id,k.code,k.name,k.tax_code,k.customer_group_id,g.code group_code,g.name group_name," +
        "k.region,k.phone,k.sales_rep_id,u.full_name sales_rep_name,u.email sales_rep_email," +
        "k.status,k.credit_limit,k.credit_days,k.created_at,k.updated_at " +
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

    // ----- S3-04: điểm giao hàng -----

    public List<Map<String, Object>> addresses(Connection c, long customerId) throws SQLException {
        return TruyVanDB.rows(
            c,
            "SELECT id,customer_id,address,recipient_name,phone,directions,is_default,updated_at " +
                "FROM customer_addresses WHERE customer_id=? AND active ORDER BY is_default DESC,id",
            customerId
        );
    }

    public Map<String, Object> addressForUpdate(Connection c, long customerId, long addressId) throws SQLException {
        return TruyVanDB.one(
            c,
            "SELECT * FROM customer_addresses WHERE id=? AND customer_id=? AND active FOR UPDATE",
            addressId, customerId
        );
    }

    public int activeAddressCount(Connection c, long customerId) throws SQLException {
        var row = TruyVanDB.one(c, "SELECT count(*) total FROM customer_addresses WHERE customer_id=? AND active", customerId);
        return ((Number) row.get("total")).intValue();
    }

    public long insertAddress(Connection c, long customerId, Map<String, Object> v, boolean isDefault) throws SQLException {
        var row = TruyVanDB.one(
            c,
            "INSERT INTO customer_addresses(customer_id,address,recipient_name,phone,directions,is_default) " +
                "VALUES(?,?,?,?,?,?) RETURNING id",
            customerId, v.get("address"), v.get("recipient_name"), v.get("phone"), v.get("directions"), isDefault
        );
        return TruyVanDB.id(row);
    }

    public void updateAddress(Connection c, long addressId, Map<String, Object> v) throws SQLException {
        TruyVanDB.update(
            c,
            "UPDATE customer_addresses SET address=?,recipient_name=?,phone=?,directions=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
            v.get("address"), v.get("recipient_name"), v.get("phone"), v.get("directions"), addressId
        );
    }

    /** Đặt một điểm giao làm mặc định, bỏ mặc định ở các điểm khác của cùng đại lý. */
    public void setDefaultAddress(Connection c, long customerId, long addressId) throws SQLException {
        TruyVanDB.update(
            c,
            "UPDATE customer_addresses SET is_default=false,updated_at=CURRENT_TIMESTAMP WHERE customer_id=? AND is_default AND id<>?",
            customerId, addressId
        );
        TruyVanDB.update(
            c,
            "UPDATE customer_addresses SET is_default=true,updated_at=CURRENT_TIMESTAMP WHERE id=? AND customer_id=? AND active",
            addressId, customerId
        );
    }

    public boolean addressUsed(Connection c, long addressId) throws SQLException {
        return TruyVanDB.one(c, "SELECT 1 FROM orders WHERE delivery_address_id=? LIMIT 1", addressId) != null;
    }

    public void deactivateAddress(Connection c, long addressId) throws SQLException {
        TruyVanDB.update(
            c,
            "UPDATE customer_addresses SET active=false,is_default=false,updated_at=CURRENT_TIMESTAMP WHERE id=?",
            addressId
        );
    }

    public void deleteAddress(Connection c, long addressId) throws SQLException {
        TruyVanDB.update(c, "DELETE FROM customer_addresses WHERE id=?", addressId);
    }

    /** Điểm giao còn dùng được, ưu tiên cũ nhất, để thay cho điểm mặc định vừa bị gỡ. */
    public Long firstActiveAddress(Connection c, long customerId) throws SQLException {
        var row = TruyVanDB.one(c, "SELECT id FROM customer_addresses WHERE customer_id=? AND active ORDER BY id LIMIT 1", customerId);
        return row == null ? null : TruyVanDB.id(row);
    }

    // ----- S3-05: hạn mức công nợ -----

    public void updateCredit(Connection c, long id, java.math.BigDecimal limit, int days) throws SQLException {
        TruyVanDB.update(
            c,
            "UPDATE customers SET credit_limit=?,credit_days=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
            limit, days, id
        );
    }

    public void insertCreditHistory(Connection c, long customerId, Map<String, Object> old,
        java.math.BigDecimal limit, int days, String reason, long actor) throws SQLException {
        TruyVanDB.update(
            c,
            "INSERT INTO customer_credit_history(customer_id,old_limit,new_limit,old_days,new_days,reason,changed_by,changed_by_name) " +
                "SELECT ?,?,?,?,?,?,u.id,u.full_name FROM users u WHERE u.id=?",
            customerId, old.get("credit_limit"), limit, old.get("credit_days"), days, reason, actor
        );
    }

    public List<Map<String, Object>> creditHistory(Connection c, long customerId) throws SQLException {
        return TruyVanDB.rows(
            c,
            "SELECT h.id,h.old_limit,h.new_limit,h.old_days,h.new_days,h.reason,h.changed_by_name,u.email changed_by_email,h.changed_at " +
                "FROM customer_credit_history h LEFT JOIN users u ON u.id=h.changed_by " +
                "WHERE h.customer_id=? ORDER BY h.changed_at DESC,h.id DESC LIMIT 200",
            customerId
        );
    }

    // ----- S3-06: phân công nhân viên kinh doanh -----

    public void setSalesRep(Connection c, long customerId, Long repId) throws SQLException {
        TruyVanDB.update(c, "UPDATE customers SET sales_rep_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", repId, customerId);
    }

    public void insertAssignment(Connection c, long customerId, Long fromId, Long toId, String reason,
        java.util.UUID batch, long actor) throws SQLException {
        TruyVanDB.update(
            c,
            "INSERT INTO customer_assignment_history(customer_id,from_user_id,from_user_name,to_user_id,to_user_name,reason,transfer_batch,changed_by,changed_by_name) " +
                "SELECT ?,?,(SELECT full_name FROM users WHERE id=?),?,(SELECT full_name FROM users WHERE id=?),?,?,a.id,a.full_name FROM users a WHERE a.id=?",
            customerId, fromId, fromId, toId, toId, reason, batch, actor
        );
    }

    public List<Map<String, Object>> assignmentHistory(Connection c, long customerId) throws SQLException {
        return TruyVanDB.rows(
            c,
            "SELECT id,from_user_id,from_user_name,to_user_id,to_user_name,reason,transfer_batch::text transfer_batch,changed_by_name,changed_at " +
                "FROM customer_assignment_history WHERE customer_id=? ORDER BY changed_at DESC,id DESC LIMIT 200",
            customerId
        );
    }

    /** Khoá và trả về ID các đại lý đang do một nhân viên phụ trách (có thể giới hạn theo danh sách chọn). */
    public List<Long> customersOfRepForUpdate(Connection c, long repId, List<Long> only) throws SQLException {
        List<Long> out = new ArrayList<>();
        var rows = only == null
            ? TruyVanDB.rows(c, "SELECT id FROM customers WHERE sales_rep_id=? ORDER BY id FOR UPDATE", repId)
            : TruyVanDB.rows(c, "SELECT id FROM customers WHERE sales_rep_id=? AND id=ANY(?) ORDER BY id FOR UPDATE",
                repId, c.createArrayOf("bigint", only.toArray()));
        for (var row : rows) out.add(TruyVanDB.id(row));
        return out;
    }

    /** Nhân viên đang hoặc từng được giao đại lý, kèm số đại lý và trạng thái tài khoản (cho màn chuyển giao). */
    public List<Map<String, Object>> repWorkload(Connection c) throws SQLException {
        return TruyVanDB.rows(
            c,
            "SELECT u.id,u.full_name,u.email,u.territory,(u.active AND (u.locked_until IS NULL OR u.locked_until<=CURRENT_TIMESTAMP)) active," +
                "EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.code='SALES_REP') is_sales_rep," +
                "(SELECT count(*) FROM customers k WHERE k.sales_rep_id=u.id) customer_count " +
                "FROM users u WHERE EXISTS(SELECT 1 FROM customers k WHERE k.sales_rep_id=u.id) " +
                "OR EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.code='SALES_REP') " +
                "ORDER BY u.full_name"
        );
    }

    public long countCustomersOfRep(Connection c, long repId) throws SQLException {
        var row = TruyVanDB.one(c, "SELECT count(*) total FROM customers WHERE sales_rep_id=?", repId);
        return ((Number) row.get("total")).longValue();
    }
}
