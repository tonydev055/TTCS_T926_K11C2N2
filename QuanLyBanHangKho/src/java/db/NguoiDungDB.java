package db;

import java.sql.*;
import java.util.*;
import util.KetNoiDB;

public class NguoiDungDB extends CoSoDB {

    public boolean deleteUser(long id, long actorId) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                boolean deleted = deleteUser(c, id, actorId);
                c.commit();
                return deleted;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public boolean deleteUser(Connection c, long id, long actorId) throws SQLException {
        if (id == actorId) throw new IllegalArgumentException(
            "Không thể tự xóa tài khoản đang đăng nhập"
        );
        var user = TruyVanDB.one(
            c,
            "SELECT id,username,email,full_name FROM users WHERE id=? FOR UPDATE",
            id
        );
        if (user == null) return false;
        TruyVanDB.update(c, "DELETE FROM users WHERE id=?", id);
        TruyVanDB.update(
            c,
            "INSERT INTO audit_logs(actor_user_id,action,object_type,object_id,old_value) VALUES(?,'DELETE','users',?,?)",
            actorId,
            String.valueOf(id),
            util.XuLyJson.stringify(user)
        );
        return true;
    }

    public Map<String, Object> findForLogin(String email) throws SQLException {
        var x = query(
            "SELECT id,email,username,full_name,phone,password_hash,active,locked_until,failed_login_attempts,session_version,territory,requires_password_change FROM users WHERE lower(email)=lower(?) OR lower(username)=lower(?)",
            email,
            email
        );
        return x.isEmpty() ? null : x.get(0);
    }

    public Map<String, Object> findById(long id) throws SQLException {
        var x = query(
            "SELECT id,email,username,full_name,phone,active,locked_until,session_version,territory,lock_reason,requires_password_change,created_at FROM users WHERE id=?",
            id
        );
        if (x.isEmpty()) return null;
        Map<String, Object> row = x.get(0);
        row.put("roles", roles(id));
        row.put("warehouses", warehouses(id));
        return row;
    }

    public List<Map<String, Object>> search(
        String search,
        String role,
        String status,
        int page,
        int size
    ) throws SQLException {
        String q = "%" + (search == null ? "" : search.trim().toLowerCase()) + "%",
            roleValue = role == null ? "" : role;
        String statusValue = status == null ? "" : status.trim().toLowerCase();
        return query(
            "SELECT u.id,u.username,u.email,u.full_name,u.phone,u.active,u.territory,u.lock_reason,u.created_at,u.last_login_at,u.failed_login_attempts,u.locked_until," +
                "(NOT u.active OR (u.locked_until IS NOT NULL AND u.locked_until>CURRENT_TIMESTAMP)) is_locked," +
                "COALESCE(string_agg(DISTINCT r.code, ','),'') roles,COALESCE(string_agg(DISTINCT w.name, ','),'') warehouses " +
                "FROM users u LEFT JOIN user_roles ur ON ur.user_id=u.id LEFT JOIN roles r ON r.id=ur.role_id " +
                "LEFT JOIN user_warehouses uw ON uw.user_id=u.id LEFT JOIN warehouses w ON w.id=uw.warehouse_id " +
                "WHERE (?='' OR lower(u.full_name) LIKE ? OR lower(u.username) LIKE ? OR lower(u.email) LIKE ? OR COALESCE(u.phone,'') LIKE ?) " +
                "AND (?='' OR EXISTS(SELECT 1 FROM user_roles x JOIN roles xr ON xr.id=x.role_id WHERE x.user_id=u.id AND xr.code=?)) " +
                "AND (?='' OR (?='active' AND u.active=true AND (u.locked_until IS NULL OR u.locked_until<=CURRENT_TIMESTAMP)) OR (?='locked' AND (u.active=false OR u.locked_until>CURRENT_TIMESTAMP))) " +
                "GROUP BY u.id ORDER BY u.created_at DESC LIMIT ? OFFSET ?",
            search == null ? "" : search.trim(),
            q,
            q,
            q,
            q,
            roleValue,
            roleValue,
            statusValue,
            statusValue,
            statusValue,
            size,
            (page - 1) * size
        );
    }

    public long count(String search, String role, String status) throws SQLException {
        String q = "%" + (search == null ? "" : search.trim().toLowerCase()) + "%",
            roleValue = role == null ? "" : role;
        String statusValue = status == null ? "" : status.trim().toLowerCase();
        var rows = query(
            "SELECT count(*) total FROM users u WHERE (?='' OR lower(u.full_name) LIKE ? OR lower(u.username) LIKE ? OR lower(u.email) LIKE ? OR COALESCE(u.phone,'') LIKE ?) AND (?='' OR EXISTS(SELECT 1 FROM user_roles x JOIN roles r ON r.id=x.role_id WHERE x.user_id=u.id AND r.code=?)) AND (?='' OR (?='active' AND u.active=true AND (u.locked_until IS NULL OR u.locked_until<=CURRENT_TIMESTAMP)) OR (?='locked' AND (u.active=false OR u.locked_until>CURRENT_TIMESTAMP)))",
            search == null ? "" : search.trim(),
            q,
            q,
            q,
            q,
            roleValue,
            roleValue,
            statusValue,
            statusValue,
            statusValue
        );
        return ((Number) rows.get(0).get("total")).longValue();
    }

    public Map<String, Object> stats() throws SQLException {
        return query(
            "SELECT count(*) total,count(*) FILTER(WHERE active=true AND (locked_until IS NULL OR locked_until<=CURRENT_TIMESTAMP)) active,count(*) FILTER(WHERE active=false OR locked_until>CURRENT_TIMESTAMP) locked,count(*) FILTER(WHERE requires_password_change=true) requires_password_change FROM users"
        ).get(0);
    }

    public List<String> roles(long userId) throws SQLException {
        List<String> out = new ArrayList<>();
        for (var row : query(
            "SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id=r.id WHERE ur.user_id=? ORDER BY r.id",
            userId
        ))
            out.add(String.valueOf(row.get("code")));
        return out;
    }

    public List<String> warehouses(long userId) throws SQLException {
        List<String> out = new ArrayList<>();
        for (var row : query(
            "SELECT w.code FROM warehouses w JOIN user_warehouses uw ON uw.warehouse_id=w.id WHERE uw.user_id=? ORDER BY w.id",
            userId
        ))
            out.add(String.valueOf(row.get("code")));
        return out;
    }

    public int sessionVersion(long userId) throws SQLException {
        var x = query("SELECT session_version FROM users WHERE id=? AND active=true", userId);
        return x.isEmpty() ? -1 : ((Number) x.get(0).get("session_version")).intValue();
    }

    public void loginFailed(long id) throws SQLException {
        update(
            "UPDATE users SET failed_login_attempts=failed_login_attempts+1,locked_until=CASE WHEN failed_login_attempts+1>=5 THEN CURRENT_TIMESTAMP+INTERVAL '15 minutes' ELSE locked_until END WHERE id=?",
            id
        );
    }

    public void loginSucceeded(long id, String upgradedHash) throws SQLException {
        if (upgradedHash == null) update(
            "UPDATE users SET failed_login_attempts=0,locked_until=NULL,last_login_at=CURRENT_TIMESTAMP WHERE id=?",
            id
        );
        else update(
            "UPDATE users SET failed_login_attempts=0,locked_until=NULL,last_login_at=CURRENT_TIMESTAMP,password_hash=? WHERE id=?",
            upgradedHash,
            id
        );
    }

    public void changePassword(long id, String encoded) throws SQLException {
        update(
            "UPDATE users SET password_hash=?,requires_password_change=false,failed_login_attempts=0,locked_until=NULL,session_version=session_version+1 WHERE id=?",
            encoded,
            id
        );
    }

    public long create(
        String username,
        String email,
        String fullName,
        String phone,
        String passwordHash,
        String territory,
        List<String> roles,
        List<String> warehouseCodes
    ) throws SQLException {
        return create(username, email, fullName, phone, passwordHash, territory, roles, warehouseCodes, true);
    }

    public long create(String username, String email, String fullName, String phone,
        String passwordHash, String territory, List<String> roles, List<String> warehouseCodes,
        boolean temporaryPassword) throws SQLException {
        username = username == null ? "" : username.trim();
        email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!username.matches("[A-Za-z0-9_.-]{3,80}")) throw new IllegalArgumentException(
            "Tên đăng nhập chỉ gồm chữ, số, dấu chấm, gạch dưới, gạch ngang (3–80 ký tự)");
        if (email.length() > 255 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Email không hợp lệ");
        validateProfile(fullName, phone);
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (
                    PreparedStatement p = c.prepareStatement(
                        "INSERT INTO users(username,email,full_name,phone,password_hash,territory,requires_password_change) VALUES(?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS
                    )
                ) {
                    p.setString(1, username);
                    p.setString(2, email);
                    p.setString(3, fullName);
                    p.setString(4, phone);
                    p.setString(5, passwordHash);
                    p.setString(6, territory);
                    p.setBoolean(7, temporaryPassword);
                    p.executeUpdate();
                    try (ResultSet r = p.getGeneratedKeys()) {
                        r.next();
                        id = r.getLong(1);
                    }
                }
                assign(c, id, roles, warehouseCodes);
                c.commit();
                return id;
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public void updateUser(
        long id,
        String fullName,
        String phone,
        String territory,
        List<String> roles,
        List<String> warehouses,
        long actorId
    ) throws SQLException {
        validateProfile(fullName, phone);
        if (id == actorId && roles.stream().noneMatch("ADMIN"::equals)) throw new SQLException(
            "Không thể tự thu hồi vai trò quản trị của chính mình"
        );
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try (
                PreparedStatement p = c.prepareStatement(
                    "UPDATE users SET full_name=?,phone=?,territory=?,session_version=session_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?"
                )
            ) {
                p.setString(1, fullName);
                p.setString(2, phone);
                p.setString(3, territory);
                p.setLong(4, id);
                p.executeUpdate();
                assign(c, id, roles, warehouses);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private void assign(Connection c, long id, List<String> roles, List<String> warehouses)
        throws SQLException {
        if (roles.isEmpty()) throw new SQLException("Người dùng phải có ít nhất một vai trò");
        if (
            (roles.contains("WAREHOUSE") || roles.contains("WH_MANAGER")) && warehouses.isEmpty()
        ) throw new SQLException("Vai trò kho phải gắn với ít nhất một kho");
        for (String role : roles) {
            if (TruyVanDB.one(c, "SELECT id FROM roles WHERE code=?", role) == null)
                throw new IllegalArgumentException("Vai trò không tồn tại: " + role);
        }
        for (String warehouse : warehouses) {
            if (TruyVanDB.one(c, "SELECT id FROM warehouses WHERE code=? AND active", warehouse) == null)
                throw new IllegalArgumentException("Mã kho không tồn tại hoặc đã ngừng hoạt động: " + warehouse);
        }
        try (PreparedStatement d = c.prepareStatement("DELETE FROM user_roles WHERE user_id=?")) {
            d.setLong(1, id);
            d.executeUpdate();
        }
        try (
            PreparedStatement p = c.prepareStatement(
                "INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?"
            )
        ) {
            for (String role : roles) {
                p.setLong(1, id);
                p.setString(2, role);
                p.addBatch();
            }
            p.executeBatch();
        }
        try (
            PreparedStatement d = c.prepareStatement("DELETE FROM user_warehouses WHERE user_id=?")
        ) {
            d.setLong(1, id);
            d.executeUpdate();
        }
        try (
            PreparedStatement p = c.prepareStatement(
                "INSERT INTO user_warehouses(user_id,warehouse_id) SELECT ?,id FROM warehouses WHERE code=?"
            )
        ) {
            for (String code : warehouses) {
                p.setLong(1, id);
                p.setString(2, code);
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    public void setLocked(long id, boolean locked, String reason) throws SQLException {
        if (locked && (reason == null || reason.isBlank())) throw new SQLException(
            "Bắt buộc ghi lý do khoá"
        );
        update(
            "UPDATE users SET active=?,lock_reason=?,failed_login_attempts=0,locked_until=NULL,session_version=session_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
            !locked,
            locked ? reason : null,
            id
        );
    }

    private static void validateProfile(String fullName, String phone) {
        if (fullName == null || fullName.isBlank() || fullName.length() > 150)
            throw new IllegalArgumentException("Họ tên phải có từ 1 đến 150 ký tự");
        service.DanhMucService.phone(phone == null ? "" : phone, false);
    }

    public void createResetToken(long userId, String hash) throws SQLException {
        update(
            "UPDATE password_reset_tokens SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL",
            userId
        );
        update(
            "INSERT INTO password_reset_tokens(user_id,token_hash,expires_at) VALUES(?,?,CURRENT_TIMESTAMP+INTERVAL '30 minutes')",
            userId,
            hash
        );
    }

    public Long consumeResetToken(String hash) throws SQLException {
        try (Connection c = KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try (
                PreparedStatement p = c.prepareStatement(
                    "SELECT id,user_id FROM password_reset_tokens WHERE token_hash=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP FOR UPDATE"
                )
            ) {
                p.setString(1, hash);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) {
                        c.rollback();
                        return null;
                    }
                    long tokenId = r.getLong(1),
                        userId = r.getLong(2);
                    try (
                        PreparedStatement u = c.prepareStatement(
                            "UPDATE password_reset_tokens SET used_at=CURRENT_TIMESTAMP WHERE id=?"
                        )
                    ) {
                        u.setLong(1, tokenId);
                        u.executeUpdate();
                    }
                    c.commit();
                    return userId;
                }
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }
}
